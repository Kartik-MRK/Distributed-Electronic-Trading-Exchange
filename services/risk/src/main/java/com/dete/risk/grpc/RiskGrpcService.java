package com.dete.risk.grpc;

import com.dete.common.domain.risk.ValidateOrderRequest;
import com.dete.common.domain.risk.ValidateOrderResponse;
import com.dete.risk.engine.RiskRuleEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** gRPC service definition implementing the ValidateOrder RPC. */
@Component
public class RiskGrpcService implements BindableService {

  private static final Logger log = LoggerFactory.getLogger(RiskGrpcService.class);

  private final RiskRuleEvaluator riskRuleEvaluator;
  private final MethodDescriptor<ValidateOrderRequest, ValidateOrderResponse> validateOrderMethod;

  public RiskGrpcService(RiskRuleEvaluator riskRuleEvaluator, ObjectMapper objectMapper) {
    this.riskRuleEvaluator = riskRuleEvaluator;
    this.validateOrderMethod = RiskGrpcContracts.createValidateOrderMethod(objectMapper);
  }

  @Override
  public ServerServiceDefinition bindService() {
    return ServerServiceDefinition.builder(RiskGrpcContracts.SERVICE_NAME)
        .addMethod(
            validateOrderMethod,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  try {
                    ValidateOrderResponse response = riskRuleEvaluator.evaluate(request);
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                  } catch (Exception e) {
                    log.error("Unhandled error in ValidateOrder gRPC call", e);
                    responseObserver.onError(e);
                  }
                }))
        .build();
  }
}
