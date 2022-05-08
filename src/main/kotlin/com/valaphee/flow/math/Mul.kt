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

package com.valaphee.flow.math

import com.valaphee.flow.DataPath
import kotlinx.coroutines.CoroutineScope

/**
 * @author Kevin Ludwig
 */
class Mul(
    override val inA: DataPath,
    override val inB: DataPath,
    override val out: DataPath
) : MathNode() {
    override fun run(scope: CoroutineScope) {
        out.set {
            val inA = inA.get()
            val inB = inB.get()
            if (inA is Number && inB is Number) when (outType(inA::class, inB::class)) {
                Byte::class -> inA.toByte() * inB.toByte()
                Short::class -> inA.toShort() * inB.toShort()
                Int::class -> inA.toInt() * inB.toInt()
                Long::class -> inA.toLong() * inB.toLong()
                Float::class -> inA.toFloat() * inB.toFloat()
                Double::class -> inA.toDouble() * inB.toDouble()
                else -> error("$inA * $inB")
            } else error("$inA * $inB")
        }
    }
}
