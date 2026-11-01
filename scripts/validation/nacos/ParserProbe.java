import com.alibaba.nacos.api.grpc.auto.*;
import com.google.protobuf.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class ParserProbe {
 static byte[] input(int count) throws Exception {
  ByteArrayOutputStream b=new ByteArrayOutputStream(); CodedOutputStream out=CodedOutputStream.newInstance(b);
  for(int i=0;i<count;i++) out.writeMessage(2,Metadata.newBuilder().putHeaders("key"+i,"v").build());
  out.flush(); return b.toByteArray();
 }
 public static void main(String[] args) throws Exception {
  Payload p=Payload.newBuilder().setMetadata(Metadata.newBuilder().setType("control").putHeaders("key","value")).setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8("{}"))).build();
  if(!p.equals(Payload.parseFrom(p.toByteArray()))) throw new AssertionError("roundtrip");
  com.google.protobuf.Descriptors.FieldDescriptor headers=Metadata.getDescriptor().findFieldByName("headers");
  Metadata meta=Metadata.newBuilder().putHeaders("key","value").build();
  Metadata reflected=Metadata.newBuilder().setField(headers,meta.getField(headers)).build();
  if(!reflected.equals(meta)||!meta.getAllFields().containsKey(headers))throw new AssertionError("public map reflection");
  if(Payload.class.getSuperclass()!=GeneratedMessageV3.class) throw new AssertionError("superclass");
  try { Payload.parseFrom(new byte[]{18,127,1}); throw new AssertionError("truncated accepted"); } catch(InvalidProtocolBufferException expected) {}
  for(byte[] bad:new byte[][]{new byte[]{18,(byte)255,(byte)255,(byte)255,(byte)255,15},new byte[]{(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,0},new byte[]{18,3,26,1,(byte)255}}) {
   try {Payload.parseFrom(bad);throw new AssertionError("malformed accepted");}catch(InvalidProtocolBufferException expected){}
  }
  byte[] groups=new byte[240];Arrays.fill(groups,0,120,(byte)27);Arrays.fill(groups,120,240,(byte)28);
  try {Payload.parseFrom(groups);throw new AssertionError("deep groups accepted");}catch(InvalidProtocolBufferException expected){}
  ByteArrayOutputStream duplicate=new ByteArrayOutputStream();CodedOutputStream duplicateOut=CodedOutputStream.newInstance(duplicate);
  duplicateOut.writeMessage(2,Metadata.newBuilder().putHeaders("same","first").build());duplicateOut.writeMessage(2,Metadata.newBuilder().putHeaders("same","last").build());duplicateOut.flush();
  if(!Payload.parseFrom(duplicate.toByteArray()).getMetadata().getHeadersOrThrow("same").equals("last"))throw new AssertionError("map overwrite");
  byte[] unknown=new byte[]{(byte)160,6,1};
  if(!Arrays.equals(Payload.parseFrom(unknown).toByteArray(),unknown))throw new AssertionError("unknown roundtrip");
  for(int n:new int[]{200,400,800,1600}) {
   byte[] data=input(n); long[] times=new long[3];
   for(int i=0;i<3;i++) {long t=System.nanoTime(); Payload result=Payload.parseFrom(data);times[i]=System.nanoTime()-t;if(result.getMetadata().getHeadersCount()!=n)throw new AssertionError("merge");}
   Arrays.sort(times);System.out.println("count="+n+" bytes="+data.length+" median_ns="+times[1]);
  }
  System.out.println("CONTROL PASS: roundtrip, merged fields, duplicate keys, unknown fields, malformed UTF8/varint/length/deep groups, V3 superclass");
 }
}
