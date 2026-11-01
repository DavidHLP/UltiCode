import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.*;
import com.alibaba.nacos.api.exception.NacosException;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
public class RealNamingProbe {
 public static void main(String[] args) throws Exception {
  Properties properties=new Properties();try(InputStream in=Files.newInputStream(Path.of(args[0]))){properties.load(in);}
  properties.setProperty("namingAsyncQuerySubscribeService","false");
  Path coordination=Path.of(args[1]);NamingService good=null,bad=null;
  try {
   Properties wrong=new Properties();wrong.putAll(properties);wrong.setProperty("password",UUID.randomUUID().toString());
   bad=NacosFactory.createNamingService(wrong);
   boolean denied=false;try{bad.registerInstance("isolated-denied-probe","127.0.0.1",32122);}catch(NacosException expected){denied=expected.getErrCode()==401 || expected.getErrCode()==403;}
   if(!denied)throw new AssertionError("invalid credentials accepted");
   bad.shutDown();bad=null;
   good=NacosFactory.createNamingService(properties);
   CountDownLatch push=new CountDownLatch(1), freshPush=new CountDownLatch(1);
   com.alibaba.nacos.api.naming.listener.EventListener listener=event->{if(event instanceof NamingEvent && ((NamingEvent)event).getInstances().stream().anyMatch(i->i.getPort()==32123))push.countDown(); if(event instanceof NamingEvent && ((NamingEvent)event).getInstances().stream().anyMatch(i->i.getPort()==32124))freshPush.countDown();};
   good.subscribe("isolated-compat-probe",listener);
   good.registerInstance("isolated-compat-probe","127.0.0.1",32123);
   if(!push.await(20,TimeUnit.SECONDS))throw new AssertionError("subscription push missing");
   if(good.getAllInstances("isolated-compat-probe").stream().noneMatch(i->i.getPort()==32123))throw new AssertionError("registration readback");
   Files.writeString(coordination.resolve("phase-one"),"ready");
   long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(150);
   while(!Files.exists(coordination.resolve("server-restarted"))){if(System.nanoTime()>end)throw new AssertionError("restart coordinator timeout");Thread.sleep(200);}
   boolean restored=false;
   while(System.nanoTime()<end){try{if(good.getAllInstances("isolated-compat-probe",false).stream().anyMatch(i->i.getPort()==32123)){restored=true;break;}}catch(NacosException ignored){}Thread.sleep(500);}
   if(!restored)throw new AssertionError("automatic reconnect and re-registration missing");
   good.registerInstance("isolated-compat-probe","127.0.0.1",32124);
   if(!freshPush.await(20,TimeUnit.SECONDS))throw new AssertionError("fresh post-reconnect subscription push missing");
   good.deregisterInstance("isolated-compat-probe","127.0.0.1",32124);
   good.deregisterInstance("isolated-compat-probe","127.0.0.1",32123);
   long removeEnd=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
   boolean removed=false;
   while(System.nanoTime()<removeEnd){if(good.getAllInstances("isolated-compat-probe",false).stream().noneMatch(i->i.getPort()==32123 || i.getPort()==32124)){removed=true;break;}Thread.sleep(200);}
   if(!removed)throw new AssertionError("remote deregistration readback failed");
   good.unsubscribe("isolated-compat-probe",listener);
   System.out.println("REAL NAMING PASS: invalid-auth rejected; authenticated register/read/subscribe/push; restart/reconnect/reregister; deregister");
  } finally {
   try{if(bad!=null)bad.shutDown();}finally{if(good!=null)good.shutDown();}
  }
 }
}
