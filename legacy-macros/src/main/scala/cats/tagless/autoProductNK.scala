/*
 * Copyright 2017 Kailuo Wang
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

package cats.tagless

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.meta._
import autoProductNK._
import Util._

import collection.immutable.Seq

/**
 * auto generates methods in companion object to compose multiple interpreters into an interpreter of a `TupleNK` effects
 */
@compileTimeOnly("Cannot expand @autoProductK")
class autoProductNK extends StaticAnnotation {
  inline def apply(defn: Any): Any = meta {
    enrich(defn)(productKInst)
  }
}

object autoProductNK {
  private[tagless] def productKInst(cls: TypeDefinition): TypeDefinition = {
    import cls._

    def productMethod(arity: Int): Stat = {
      val range = (1 to arity).map(_.toString)

      // "F1, F2, F3"
      val effectTypeParamsNames = range.map(n => Type.Name("F" + n))

      //Tuple3K
      val productTypeName = Type.Name(s"_root_.cats.tagless.Tuple${arity}K")

      val methods = templ.stats.map(_.map {
        case q"def $methodName[..$mTParams](..$params): $f[$resultType]" =>
          val returnItems = range.map { n =>
            q"${Term.Name("af" + n)}.$methodName(..${arguments(params)})"
          }
          q"""def $methodName[..$mTParams](..$params): $productTypeName[..$effectTypeParamsNames]#λ[$resultType] =
           (..$returnItems)"""
        //curried version
        case q"def $methodName[..$mTParams](..$params)(..$params2): $f[$resultType]" =>
          val returnItems = range.map { n =>
            q"${Term.Name("af" + n)}.$methodName(..${arguments(params)})(..${arguments(params2)})"
          }
          q"""def $methodName[..$mTParams](..$params)(..$params2): $productTypeName[..$effectTypeParamsNames]#λ[$resultType] =
           (..$returnItems)"""
        case st => abort(s"autoProductK does not support algebra with such statement: $st")
      }).getOrElse(Nil)

      // tparam"F1[_], F2[_], F3[_]"
      val effectTypeParams: Seq[Type.Param] = range.map(n => typeParam(s"F$n", 1))

      // param"af1: A[F1]"
      def inboundInterpreter(idx: String): Term.Param =
        Term.Param(Nil, Term.Name("af" + idx), Some(Type.Name(s"${name.value}[F"+idx + "]")), None)

      //af1: A[F1], af2: A[F2], af3: A[F3]
      val inboundInterpreters: Seq[Term.Param] = range.map(inboundInterpreter)

      q"""
        def ${Term.Name("product" + arity + "K")}[..$effectTypeParams](..$inboundInterpreters): $name[$productTypeName[..$effectTypeParamsNames]#λ] =
          new ${Ctor.Ref.Name(name.value)}[$productTypeName[..$effectTypeParamsNames]#λ] {
            ..$methods
          }
      """

    }

    val instanceDef = (3 to 9).map(productMethod)

    cls.copy(companion = cls.companion.addStats(instanceDef))
  }
}




