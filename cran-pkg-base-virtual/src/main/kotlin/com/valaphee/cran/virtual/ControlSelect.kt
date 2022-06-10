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

package com.valaphee.cran.virtual

import com.valaphee.cran.Virtual
import com.valaphee.cran.node.Node
import com.valaphee.cran.node.control.Select
import com.valaphee.cran.spec.NodeImpl
import kotlinx.coroutines.CoroutineScope

/**
 * @author Kevin Ludwig
 */
@NodeImpl("virtual")
object ControlSelect : Implementation {
    override fun initialize(coroutineScope: CoroutineScope, node: Node, virtual: Virtual) = if (node is Select) {
        val `in` = virtual.dataPath(node.`in`)
        val inValue = node.inValue.associate { it.key to virtual.dataPath(it.value) }
        val inDefault = virtual.dataPath(node.inDefault)
        val out = virtual.dataPath(node.out)

        out.set { (inValue[`in`.getOfType()] ?: inDefault).get() }

        true
    } else false
}
