package com.dete.risk.grpc;

import com.dete.common.domain.risk.ValidateOrderRequest;
import com.dete.common.domain.risk.ValidateOrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.MethodDescriptor;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** gRPC method descriptors and custom JSON marshallers for Risk Service RPCs. */
public final class RiskGrpcContracts {

  public static final String SERVICE_NAME = "com.dete.risk.proto.RiskService";
  public static final String METHOD_VALIDATE_ORDER_NAME = "ValidateOrder";

  private RiskGrpcContracts() {}

  public static MethodDescriptor<ValidateOrderRequest, ValidateOrderResponse>
      createValidateOrderMethod(ObjectMapper mapper) {
    return MethodDescriptor.<ValidateOrderRequest, ValidateOrderResponse>newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(
            MethodDescriptor.generateFullMethodName(SERVICE_NAME, METHOD_VALIDATE_ORDER_NAME))
        .setSampledToLocalTracing(true)
        .setRequestMarshaller(new JsonMarshaller<>(ValidateOrderRequest.class, mapper))
        .setResponseMarshaller(new JsonMarshaller<>(ValidateOrderResponse.class, mapper))
        .build();
  }

  public static class JsonMarshaller<T> implements MethodDescriptor.Marshaller<T> {
    private final Class<T> targetClass;
    private final ObjectMapper objectMapper;

    public JsonMarshaller(Class<T> targetClass, ObjectMapper objectMapper) {
      this.targetClass = targetClass;
      this.objectMapper = objectMapper;
    }

    @Override
    public InputStream stream(T value) {
      try {
        return new ByteArrayInputStream(objectMapper.writeValueAsBytes(value));
      } catch (Exception e) {
        throw new RuntimeException("Failed to serialize gRPC message", e);
      }
    }

    @Override
    public T parse(InputStream stream) {
      try {
        return objectMapper.readValue(stream, targetClass);
      } catch (Exception e) {
        throw new RuntimeException("Failed to deserialize gRPC message", e);
      }
    }
  }
}
