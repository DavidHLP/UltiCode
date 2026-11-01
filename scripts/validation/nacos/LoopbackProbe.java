import com.alibaba.nacos.api.grpc.auto.*;
import com.google.protobuf.*;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.*;
import io.grpc.stub.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
public class LoopbackProbe {
 public static void main(String[] args) throws Exception {
  if(!Boolean.getBoolean("com.google.protobuf.error_on_unsafe_pre22_gencode"))throw new AssertionError("strict mode required");
  AtomicInteger handled=new AtomicInteger();
  Server server=NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1",0)).maxInboundMessageSize(1024*1024)
    .addService(new RequestGrpc.RequestImplBase(){public void request(Payload p,StreamObserver<Payload> r){handled.incrementAndGet();r.onNext(p);r.onCompleted();}}).addService(new BiRequestStreamGrpc.BiRequestStreamImplBase(){
      public StreamObserver<Payload> requestBiStream(StreamObserver<Payload> response){
       return new StreamObserver<Payload>(){public void onNext(Payload value){response.onNext(value);}public void onError(Throwable t){response.onError(t);}public void onCompleted(){response.onCompleted();}};
      }
    }).build().start();
  ManagedChannel channel=null;
  try {
   channel=NettyChannelBuilder.forAddress("127.0.0.1",server.getPort()).usePlaintext().maxInboundMessageSize(1024*1024).build();
   Payload p=Payload.newBuilder().setMetadata(com.alibaba.nacos.api.grpc.auto.Metadata.newBuilder().setType("ServerCheckRequest")).setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8("{}"))).build();
   if(!p.equals(RequestGrpc.newBlockingStub(channel).withDeadlineAfter(5,TimeUnit.SECONDS).request(p)))throw new AssertionError("normal");
   CountDownLatch finished=new CountDownLatch(1);AtomicInteger received=new AtomicInteger();AtomicReference<Throwable> streamError=new AtomicReference<>();
   StreamObserver<Payload> send=BiRequestStreamGrpc.newStub(channel).withDeadlineAfter(5,TimeUnit.SECONDS).requestBiStream(new StreamObserver<Payload>(){
    public void onNext(Payload value){if(!value.equals(p))streamError.set(new AssertionError("stream value"));received.incrementAndGet();}
    public void onError(Throwable t){streamError.set(t);finished.countDown();}
    public void onCompleted(){finished.countDown();}
   });
   send.onNext(p);send.onNext(p);send.onCompleted();
   if(!finished.await(6,TimeUnit.SECONDS)||streamError.get()!=null||received.get()!=2)throw new AssertionError("bidirectional stream",streamError.get());
   MethodDescriptor<byte[],Payload> raw=RequestGrpc.getRequestMethod().toBuilder(new MethodDescriptor.Marshaller<byte[]>(){
    public InputStream stream(byte[] b){return new ByteArrayInputStream(b);} public byte[] parse(InputStream in){throw new UnsupportedOperationException();}
   },RequestGrpc.getRequestMethod().getResponseMarshaller()).build();
   Payload many=ClientCalls.blockingUnaryCall(channel,raw,CallOptions.DEFAULT.withDeadlineAfter(5,TimeUnit.SECONDS),ParserProbe.input(1600));
   if(many.getMetadata().getHeadersCount()!=1600)throw new AssertionError("merge");
   int before=handled.get();
   try {ClientCalls.blockingUnaryCall(channel,raw,CallOptions.DEFAULT.withDeadlineAfter(5,TimeUnit.SECONDS),new byte[]{18,127,1});throw new AssertionError("malformed accepted");}
   catch(StatusRuntimeException expected){if(expected.getStatus().getCode()!=Status.Code.INTERNAL && expected.getStatus().getCode()!=Status.Code.UNKNOWN)throw expected;}
   if(handled.get()!=before)throw new AssertionError("malformed reached service");
   try {ClientCalls.blockingUnaryCall(channel,raw,CallOptions.DEFAULT.withDeadlineAfter(5,TimeUnit.SECONDS),new byte[1024*1024+1]);throw new AssertionError("oversize accepted");}
   catch(StatusRuntimeException expected){if(expected.getStatus().getCode()!=Status.Code.RESOURCE_EXHAUSTED)throw expected;}
   if(handled.get()!=before)throw new AssertionError("oversize reached service");
   System.out.println("LOOPBACK PASS: unary, bidirectional stream, repeated metadata, malformed/oversized rejected before handler; strict gencode enabled");
  } finally {
   try {if(channel!=null && !channel.shutdownNow().awaitTermination(5,TimeUnit.SECONDS))throw new AssertionError("channel cleanup");}
   finally {if(!server.shutdownNow().awaitTermination(5,TimeUnit.SECONDS))throw new AssertionError("server cleanup");}
  }
 }
}
