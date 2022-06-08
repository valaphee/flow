/*
 * Copyright (c) 2022, Valaphee.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.valaphee.cran.graph

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.inject.Inject
import com.google.inject.Singleton
import com.valaphee.cran.graph.jvm.GraphJvmLookup
import com.valaphee.cran.graph.jvm.Scope
import com.valaphee.cran.node.Entry
import com.valaphee.cran.node.NodeJvm
import com.valaphee.cran.spec.Spec
import com.valaphee.cran.svc.graph.v1.DeleteGraphRequest
import com.valaphee.cran.svc.graph.v1.DeleteGraphResponse
import com.valaphee.cran.svc.graph.v1.GetSpecRequest
import com.valaphee.cran.svc.graph.v1.GetSpecResponse
import com.valaphee.cran.svc.graph.v1.GraphServiceGrpc.GraphServiceImplBase
import com.valaphee.cran.svc.graph.v1.ListGraphRequest
import com.valaphee.cran.svc.graph.v1.ListGraphResponse
import com.valaphee.cran.svc.graph.v1.PathProbeRequest
import com.valaphee.cran.svc.graph.v1.PathProbeResponse
import com.valaphee.cran.svc.graph.v1.RunGraphRequest
import com.valaphee.cran.svc.graph.v1.RunGraphResponse
import com.valaphee.cran.svc.graph.v1.StopGraphRequest
import com.valaphee.cran.svc.graph.v1.StopGraphResponse
import com.valaphee.cran.svc.graph.v1.UpdateGraphRequest
import com.valaphee.cran.svc.graph.v1.UpdateGraphResponse
import io.github.classgraph.ClassGraph
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * @author Kevin Ludwig
 */
@Singleton
class GraphServiceImpl @Inject constructor(
    private val objectMapper: ObjectMapper
) : GraphServiceImplBase(), GraphJvmLookup, CoroutineScope {
    private val executor = Executors.newSingleThreadExecutor()
    override val coroutineContext get() = executor.asCoroutineDispatcher()

    private val dataPath = File("data").also { it.mkdirs() }

    private val spec: Spec
    private val graphs = mutableMapOf<String, GraphImpl>()
    private val scopes = mutableMapOf<UUID, Scope>()

    init {
        ClassGraph().scan().use {
            spec = it.getResourcesMatchingWildcard("**.spec.json").urLs.map { objectMapper.readValue<Spec>(it).also { it.nodes.onEach { log.info("Built-in node '{}' found", it.name) } } }.reduce { acc, spec -> acc + spec }
            graphs += it.getResourcesMatchingWildcard("**.gph").urLs.map { objectMapper.readValue<GraphImpl>(it).also { log.info("Built-in graph '{}' found", it.name) } }.associateBy { it.name }
        }
        dataPath.walk().forEach {
            if (it.isFile && it.extension == "gph") it.inputStream().use {
                objectMapper.readValue<GraphImpl>(GZIPInputStream(it)).also {
                    graphs[it.name] = it

                    log.info("Graph '{}' found", it.name)
                }
            }
        }
    }

    override fun getGraphJvm(name: String) = graphs[name]

    override fun getSpec(request: GetSpecRequest, responseObserver: StreamObserver<GetSpecResponse>) {
        responseObserver.onNext(GetSpecResponse.newBuilder().setSpec(objectMapper.writeValueAsString(spec)).build())
        responseObserver.onCompleted()
    }

    override fun listGraph(request: ListGraphRequest, responseObserver: StreamObserver<ListGraphResponse>) {
        responseObserver.onNext(ListGraphResponse.newBuilder().apply { addAllGraphs(graphs.values.map { objectMapper.writeValueAsString(it) }) }.build())
        responseObserver.onCompleted()
    }

    override fun updateGraph(request: UpdateGraphRequest, responseObserver: StreamObserver<UpdateGraphResponse>) {
        val graph = objectMapper.readValue<GraphImpl>(request.graph.toByteArray())
        graphs[graph.name] = graph
        File(dataPath, graph.toFile()).outputStream().use { objectMapper.writeValue(GZIPOutputStream(it), graph) }

        responseObserver.onNext(UpdateGraphResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun deleteGraph(request: DeleteGraphRequest, responseObserver: StreamObserver<DeleteGraphResponse>) {
        graphs.remove(request.graphName)?.let { File(dataPath, it.toFile()).delete() }

        responseObserver.onNext(DeleteGraphResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun runGraph(request: RunGraphRequest, responseObserver: StreamObserver<RunGraphResponse>) {
        val scopeId = UUID.randomUUID()
        graphs[request.graphName]?.let {
            val scope = Scope(objectMapper, checkNotNull(spec.nodeProcs["jvm"]).mapNotNull { Class.forName(it).kotlin.objectInstance as NodeJvm? }.toSet(), this, it).also {
                scopes[scopeId] = it
                it.process()
            }
            it.nodes.forEach { if (it is Entry) launch { scope.controlPath(it.out)() } }
        }

        responseObserver.onNext(RunGraphResponse.newBuilder().setScopeId(scopeId.toString()).build())
        responseObserver.onCompleted()
    }

    override fun stopGraph(request: StopGraphRequest, responseObserver: StreamObserver<StopGraphResponse>) {
        scopes.remove(UUID.fromString(request.scopeId))

        responseObserver.onNext(StopGraphResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun pathProbe(responseObserver: StreamObserver<PathProbeResponse>) = object : StreamObserver<PathProbeRequest> {
        override fun onNext(value: PathProbeRequest) {
        }

        override fun onError(thrown: Throwable) {
        }

        override fun onCompleted() {
            responseObserver.onCompleted()
        }
    }

    companion object {
        private val log = LogManager.getLogger(GraphServiceImpl::class.java)

        private fun Graph.toFile() = "${Base64.getUrlEncoder().encodeToString(name.lowercase().toByteArray())}.gph"
    }
}
