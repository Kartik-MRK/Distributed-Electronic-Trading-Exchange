package com.dete.simulator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dete.common.domain.enums.Instrument;
import com.dete.simulator.config.SimulatorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExchangeRestClientTest {

  @Test
  void readsReferencePriceFromRiskService() {
    SimulatorProperties properties = new SimulatorProperties();
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://localhost:8085/risk/instruments"))
        .andRespond(
            withSuccess(
                "{\"BTC_USD\":6000000000000,\"ETH_USD\":300000000000,\"SOL_USD\":15000000000}",
                MediaType.APPLICATION_JSON));

    ExchangeRestClient client = new ExchangeRestClient(properties, builder.build());

    assertThat(client.getRiskReferencePrice(Instrument.ETH_USD)).isEqualTo(300000000000L);
    server.verify();
  }
}
