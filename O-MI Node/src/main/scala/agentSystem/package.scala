
/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package object agentSystem{
  type AgentName = String
  object Language{
    def apply( str: String ) = str.toLowerCase() match {
      case "java" => Java()
      case "scala" => Scala()
      case str: String => Unknown(str)
    }
  }
  sealed trait Language 
  final case class Unknown(val lang : String ) extends Language
  final case class Scala() extends Language
  final case class Java() extends Language
}
