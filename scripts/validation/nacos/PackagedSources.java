import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.grpc.auto.*;
import java.util.*;
public class PackagedSources {
 public static void main(String[] args) throws Exception {
  for(Class<?> cls:new Class<?>[]{Metadata.class,Payload.class,NacosGrpcService.class,RequestGrpc.class,NacosFactory.class}) {
   String source=cls.getProtectionDomain().getCodeSource().getLocation().toString();
   if(!source.contains("nacos-api-compat"))throw new AssertionError("unexpected source");
   if(Collections.list(cls.getClassLoader().getResources(cls.getName().replace('.','/')+".class")).size()!=1)throw new AssertionError("duplicate class");
   System.out.println(cls.getName()+" -> "+source);
  }
 }
}
