package com.ildcvf.traffic.proxy;

import com.ildcvf.traffic.client.ForwardedResponse;
import com.ildcvf.traffic.service.TrafficProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class TrafficProxyController {

    private final TrafficProxyService trafficProxyService;

    public TrafficProxyController(
            TrafficProxyService trafficProxyService
    ) {
        this.trafficProxyService =
                trafficProxyService;
    }

    @RequestMapping(
            value = {
                    "/proxy/api/residents",
                    "/proxy/api/residents/**"
            },
            method = {
                    RequestMethod.GET,
                    RequestMethod.POST,
                    RequestMethod.PUT,
                    RequestMethod.DELETE
            }
    )
    public ResponseEntity<byte[]> proxyResidentRequest(
            HttpServletRequest request,
            @RequestBody(required = false)
            byte[] body
    ) {
        ForwardedResponse response =
                trafficProxyService.proxy(
                        request,
                        body
                );

        return ResponseEntity
                .status(response.status())
                .headers(response.headers())
                .body(response.body());
    }
}