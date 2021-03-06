package http

import java.util.Date
import java.sql.Timestamp
import java.net.InetSocketAddress//(String hostname, int port)
import scala.collection.mutable.{ Map => MutableMap }
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util.{ByteString, Timeout}
import akka.actor.{Actor, ActorSystem, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.testkit._
import com.typesafe.config.{ConfigFactory, Config}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.create.InterpolatedFragment
import org.specs2.mutable._
import org.specs2.matcher._
import org.specs2.matcher.FutureMatchers._
import com.typesafe.config.{ConfigFactory, Config}
import testHelpers.Actorstest
import types.Path
import types.OmiTypes._
import responses.{RemoveHandlerT,RemoveSubscription}
import agentSystem.{AgentName, AgentInfo, TestManager, SSAgent}
import akka.util.{ByteString, Timeout}
import akka.http.scaladsl.model.Uri
import akka.io.Tcp.{Write, Received}
import database.{EventSub, IntervalSub, PolledSub, PollEventSub, PollIntervalSub, SavedSub}
import http.CLICmds._




class NodeCLITest(implicit ee: ExecutionEnv) extends Specification{
  "NodeCLI should " >> {
    "return list of available commands when help command is received" >> helpTest
    "return table of agents when list agents command is received" >> listAgentsTest
    "return correct message when start agent command is received" >> startAgentTest
    "return correct message when stop agent command is received" >> stopAgentTest
    "return help information when unknown command is received" >> unknownCmdTest
    "return correct message when path remove command is received" >> removePathTest
    "return correct message when path remove command for unexisting path is received" >> removeUnexistingPathTest
    "return table of subscriptions when list subs command is received" >> listSubsTest
    "return correct message when sub remove command is received" >> removeSubTest
    "return correct message when sub remove command for unexisting id is received" >> removeUnexistingSubTest
    "return correct message when show sub command is received for" >>{
      "Interval subscription" >> showSubTestInterval
      "Event subscription" >> showSubTestEvent
      "Polled Interval subscription" >> showSubTestPollInterval 
      "Polled Event subscription" >> showSubTestPollEvent
      "nonexistent subscription" >> showSubTestNonexistent
    }
  }
  implicit val timeout = Timeout( 1.minutes )
  def timeoutDuration= 10.seconds
  def emptyConfig = ConfigFactory.empty()
  def AS =ActorSystem(
    "startstop",
    ConfigFactory.load(
      ConfigFactory.parseString(
        """
        akka.loggers = ["akka.testkit.TestEventListener"]
        """).withFallback(ConfigFactory.load()))
    )
  def strToMsg(str: String) = Received(ByteString(str))
  def decodeWriteStr( future : Future[Any] )(implicit system: ActorSystem) ={
    import system.dispatcher
    future.map{  
      case Write( byteStr: ByteString, _ ) => byteStr.decodeString("UTF-8")
    }
  }

  class RemoveTester( path: Path)extends RemoveHandlerT{

    override def handlePathRemove(parentPath: Path): Boolean = { 
      path == parentPath || path.isAncestor(parentPath)
    }
  }
  def helpTest = new Actorstest(AS){
    import system.dispatcher
    val agentSystem =ActorRef.noSender
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg("help"))    
    val correct: String  = listener.commands 
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }
  def listAgentsTest= new Actorstest(AS){
    import system.dispatcher
    val agents = Vector(
      AgentInfo( "test1", "testClass", emptyConfig, ActorRef.noSender, running = true, Nil ),
      AgentInfo( "test2", "testClass", emptyConfig, ActorRef.noSender, running = true, Nil ),
      AgentInfo( "test3", "testClass2", emptyConfig, ActorRef.noSender, running = true, Nil ),
      AgentInfo( "test4", "testClass2", emptyConfig, ActorRef.noSender, running = false, Nil )
    ).sortBy{info => info.name }
    val agentsMap : MutableMap[AgentName,AgentInfo] = MutableMap(agents.map{ info => info.name -> info }:_*)
    val agentSystemRef = TestActorRef(new TestManager( agentsMap ))
    val agentSystem = agentSystemRef.underlyingActor
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      agentSystemRef,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF :Future[String ]=decodeWriteStr(listenerRef ? strToMsg("list agents"))    
    val correct : String =  listener.agentsStrChart( agents) 
    resF should beEqualTo( correct ).await( 0, timeoutDuration)
  }

  def startAgentTest=  new Actorstest(AS){

    val name = "StartSuccess"
    val ref = system.actorOf( Props( new SSAgent), name)
    val clazz = "agentSystem.SSAgent"
    val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, running = false, Nil)
    val testAgents = MutableMap( name -> agentInfo)
    val managerRef = TestActorRef( new TestManager(testAgents)) 
    val managerActor = managerRef.underlyingActor
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      managerRef,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF :Future[String ]=decodeWriteStr(listenerRef ? strToMsg(s"start $name"))    
    val correct : String =  s"Agent $name started succesfully.\n" 
    resF should beEqualTo( correct ).await( 0, timeoutDuration)

  }
  def stopAgentTest= new Actorstest(AS){
    val name = "StartSuccess"
    val ref = system.actorOf( Props( new SSAgent), name)
    val clazz = "agentSystem.SSAgent"
    val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, running = true, Nil)
    val testAgents = MutableMap( name -> agentInfo)
    val managerRef = TestActorRef( new TestManager(testAgents)) 
    val managerActor = managerRef.underlyingActor
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      managerRef,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF :Future[String ]=decodeWriteStr(listenerRef ? strToMsg(s"stop $name"))    
    val correct : String =  s"Agent $name stopped succesfully.\n" 
    resF should beEqualTo( correct ).await( 0, timeoutDuration)

  }
  def unknownCmdTest = new Actorstest(AS){
    import system.dispatcher
    val agentSystem =ActorRef.noSender
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg("aueo"))    
    val correct: String  = "Unknown command. Use help to get information of current commands.\n" 
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }
  def removePathTest= new Actorstest(AS){
    import system.dispatcher
    val agentSystem =ActorRef.noSender
    val subscriptionManager = ActorRef.noSender
    val path = "Objects/object/sensor"
    val removeHandler = new RemoveTester( Path(path) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg(s"remove $path"))    
    val correct: String  = s"Successfully removed path $path\n" 
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }
  def removeUnexistingPathTest= new Actorstest(AS){
    import system.dispatcher
    val agentSystem =ActorRef.noSender
    val subscriptionManager = ActorRef.noSender
    val path = "Objects/object/sensor"
    val removeHandler = new RemoveTester( Path(path) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg(s"remove " + path +"ueaueo" ))    
    val correct: String  = s"Given path does not exist\n" 
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }

  def listSubsTest= new Actorstest(AS){
    import system.dispatcher
    val startTime = new Timestamp( new Date().getTime() )
    val endTime = new Timestamp( new Date().getTime() + 1.hours.toMillis )
    val interval = 5.minutes
    val nextRunTime = new Timestamp( new Date().getTime() + interval.toMillis)
    val callback = HTTPCallback(Uri("http://test.org:31"))
    val paths = Vector( 
      Path( "Objects/object/sensor1" ),
      Path( "Objects/object/sensor2" )
    )
    val intervalSubs : Set[IntervalSub] = Set( 
      IntervalSub( 35, paths, endTime, callback, interval, startTime ),
      IntervalSub( 55, paths, endTime, callback, interval, startTime )
    )
    val eventSubs : Set[EventSub] =Set( 
      EventSub( 40, paths, endTime, callback),
      EventSub( 430, paths, endTime, callback),
      EventSub( 32, paths, endTime, callback)
    )
    val pollSubs : Set[PolledSub] = Set( 
      PollEventSub(59, endTime, nextRunTime, startTime, paths),
      PollEventSub(173, endTime, nextRunTime, startTime, paths),
      PollIntervalSub(37,endTime,interval,nextRunTime,startTime,paths),
      PollIntervalSub(3047,endTime,interval,nextRunTime,startTime,paths)
    )
    val agentSystem =ActorRef.noSender
    val subscriptionManager = TestActorRef( new Actor{
      def receive = {
        case ListSubsCmd() => sender() ! (intervalSubs, eventSubs, pollSubs) 
      }

    })
    val removeHandler = new RemoveTester( Path("objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg("list subs"))    
    val correct: String  = listener.subsStrChart(intervalSubs, eventSubs, pollSubs)
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }

  def removeSubTest= new Actorstest(AS){
    import system.dispatcher
    val id = 13
    val agentSystem =ActorRef.noSender
    val subscriptionManager = TestActorRef( new Actor{
      def receive = {
        case RemoveSubscription(di) => sender() ! true
      }

    })
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg(s"remove $id"))    
    val correct: String  =s"Removed subscription with $id successfully.\n" 
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }

  def removeUnexistingSubTest= new Actorstest(AS){
    import system.dispatcher
    val id = 13
    val agentSystem =ActorRef.noSender
    val subscriptionManager = TestActorRef( new Actor{
      def receive = {
        case RemoveSubscription(di) => sender() ! false
      }

    })
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg(s"remove $id"))    
    val correct: String  =s"Failed to remove subscription with $id. Subscription does not exist or it is already expired.\n"
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }
  def showSubTestInterval = {
    val id: Long = 57171
    val paths = Vector( Path( "Objects/obj1/"), Path( "Objects/obj2/sensor1"), Path( "Objects/obj3/sobj/sensor"))  
    val startTime = new Timestamp( new Date().getTime() )
    val endTime = new Timestamp( new Date().getTime() + 1.hours.toMillis )
    val interval = 5.minutes
    val nextRunTime = new Timestamp( new Date().getTime() + interval.toMillis)
    val callback = HTTPCallback(Uri("http://test.org:31"))

    val remote = new InetSocketAddress("Tester",22)
    val agentSystem =ActorRef.noSender
    val removeHandler = new RemoveTester( Path("objects/aue" ) )
    val sub = Some(IntervalSub(
      id,
      paths,
      endTime,
      callback,
      interval,
      startTime
    ))
    val correct: String  =s"Started: ${startTime}\n" +
    s"Ends: ${endTime}\n" +
    s"Interval: ${interval}\n" +
    s"Callback: ${callback}\n" +
    s"Paths:\n${paths.mkString("\n")}\n"
    showSubTestBase(sub,correct)
  }

  def showSubTestEvent = {
    val id: Long = 57171
    val paths = Vector( Path( "Objects/obj1/"), Path( "Objects/obj2/sensor1"), Path( "Objects/obj3/sobj/sensor"))  
    val startTime = new Timestamp( new Date().getTime() )
    val endTime = new Timestamp( new Date().getTime() + 1.hours.toMillis )
    val interval = 5.minutes
    val nextRunTime = new Timestamp( new Date().getTime() + interval.toMillis)
    val callback = HTTPCallback(Uri("http://test.org:31"))

    val remote = new InetSocketAddress("Tester",22)
    val agentSystem =ActorRef.noSender
    val removeHandler = new RemoveTester( Path("objects/aue" ) )
    val sub = Some(EventSub(
      id,
      paths,
      endTime,
      callback
    ))
    val correct: String  =s"Ends: ${endTime}\n" +
    s"Callback: ${callback}\n" +
    s"Paths:\n${paths.mkString("\n")}\n"
    showSubTestBase(sub,correct)
  }

  def showSubTestPollInterval = {
    val id: Long = 57171
    val paths = Vector( Path( "Objects/obj1/"), Path( "Objects/obj2/sensor1"), Path( "Objects/obj3/sobj/sensor"))  
    val startTime = new Timestamp( new Date().getTime() )
    val endTime = new Timestamp( new Date().getTime() + 1.hours.toMillis )
    val interval = 5.minutes
    val nextRunTime = new Timestamp( new Date().getTime() + interval.toMillis)
    val callback = HTTPCallback(Uri("http://test.org:31"))

    val remote = new InetSocketAddress("Tester",22)
    val agentSystem =ActorRef.noSender
    val removeHandler = new RemoveTester( Path("objects/aue" ) )
    val sub = Some(PollIntervalSub(
      id,
      endTime,
      interval,
      nextRunTime,
      startTime,
      paths
    ))
    val correct: String  =s"Started: ${startTime}\n" +
    s"Ends: ${endTime}\n" +
    s"Interval: ${interval}\n" +
    s"Last polled: ${nextRunTime}\n" +
    s"Paths:\n${paths.mkString("\n")}\n"
    showSubTestBase(sub,correct)
  }

  def showSubTestPollEvent = { 
    val id: Long = 57171
    val paths = Vector( Path( "Objects/obj1/"), Path( "Objects/obj2/sensor1"), Path( "Objects/obj3/sobj/sensor"))  
    val startTime = new Timestamp( new Date().getTime() )
    val endTime = new Timestamp( new Date().getTime() + 1.hours.toMillis )
    val interval = 5.minutes
    val nextRunTime = new Timestamp( new Date().getTime() + interval.toMillis)
    val callback = HTTPCallback(Uri("http://test.org:31"))

    val remote = new InetSocketAddress("Tester",22)
    val agentSystem =ActorRef.noSender
    val removeHandler = new RemoveTester( Path("objects/aue" ) )
    val sub = Some(PollEventSub(
      id,
      endTime,
      nextRunTime,
      startTime,
      paths
    ))
    val correct: String  = s"Started: ${startTime}\n" +
    s"Ends: ${endTime}\n" +
    s"Last polled: ${nextRunTime}\n" +
    s"Paths:\n${paths.mkString("\n")}\n"
    showSubTestBase(sub,correct)
  }

  def showSubTestNonexistent = {
    val id: Long = 57171
    val paths = Vector( Path( "Objects/obj1/"), Path( "Objects/obj2/sensor1"), Path( "Objects/obj3/sobj/sensor"))  
    val startTime = new Timestamp( new Date().getTime() )
    val endTime = new Timestamp( new Date().getTime() + 1.hours.toMillis )
    val interval = 5.minutes
    val nextRunTime = new Timestamp( new Date().getTime() + interval.toMillis)
    val callback = HTTPCallback(Uri("http://test.org:31"))

    val remote = new InetSocketAddress("Tester",22)
    val agentSystem =ActorRef.noSender
    val removeHandler = new RemoveTester( Path("objects/aue" ) )
    val correct: String  = s"Subscription with id $id not found.\n" 
    showSubTestBase(None,correct)
  }
  def showSubTestBase( sub: Option[SavedSub], correctOut: String ) = new Actorstest(AS){
    val correct = correctOut
    val remote = new InetSocketAddress("Tester",22)
    val agentSystem =ActorRef.noSender
    val removeHandler = new RemoveTester( Path("objects/aue" ) )
    val subscriptionManager = TestActorRef( new Actor{
      def receive = {
        case SubInfoCmd(id) => sender() ! sub
      }
    })
    val listenerRef = TestActorRef(new OmiNodeCLI(
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg(s"showSub ${sub.map{s => s.id}.getOrElse(57171)}"))    
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }
}
