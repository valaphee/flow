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

package com.valaphee.cran.node.control

import com.valaphee.cran.graph.jvm.Scope
import com.valaphee.cran.node.Node
import com.valaphee.cran.node.NodeJvm
import com.valaphee.cran.spec.NodeProc

/**
 * @author Kevin Ludwig
 */
@NodeProc("jvm")
object BranchJvm : NodeJvm {
    override fun process(nodes: List<Node>, scope: Scope) {
        nodes.forEach {
            if (it is Branch) {
                val `in` = scope.controlPath(it.`in`)
                val inValue = scope.dataPath(it.inValue)
                val out = it.out.mapValues { scope.controlPath(it.value) }
                val outDefault = scope.controlPath(it.outDefault)

                `in`.declare { (out[inValue.get()] ?: outDefault)() }
            }
        }
    }
}
