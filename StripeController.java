package com.example.api.controller;

import com.example.api.dto.CheckoutRequest;
import com.example.api.dto.DeviceSubscriptionDto;
import com.example.api.enums.SubscriptionInterval;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("payments")
public class StripeController {

    @Value("${frontend.success.url}")
    private String successUrl;

    @Value("${frontend.cancel.url}")
    private String cancelUrl;

    private final String yearlyPriceId = "price_1R9fhL4dVqcfr57O7J6iXO7T";
    private final String monthlyPriceId = "price_1R9fgZ4dVqcfr57OEJefJV7s";

    @PostMapping("/create-checkout-session")
    public ResponseEntity<String> createCheckoutSession(@RequestBody CheckoutRequest checkoutRequest) throws StripeException {
        List<SessionCreateParams.LineItem> lineItems = new ArrayList<>();
        lineItems.add(
                SessionCreateParams.LineItem.builder()
                        .setPrice(getPriceId(checkoutRequest.subscriptionInterval()))
                        .setQuantity(1L)
                        .build()
        );

        String deviceId = (String) SecurityContextHolder.getContext().getAuthentication().getCredentials();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addAllLineItem(lineItems)
                .setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder().putMetadata("deviceId", deviceId).build())
                .build();

        Session session = Session.create(params);
        return ResponseEntity.ok(session.getUrl());
    }

    @DeleteMapping("/subscription/{subscriptionId}")
    public ResponseEntity<DeviceSubscriptionDto> cancelSubscription(@PathVariable String subscriptionId) {
        try {
            Subscription subscription = Subscription.retrieve(subscriptionId);
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build();
            Subscription updated = subscription.update(params);
            return ResponseEntity.ok(DeviceSubscriptionDto.from(updated));
        } catch (StripeException e) {
            System.out.println("Unable to update Stripe subscription!");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/subscription/{subscriptionId}")
    public ResponseEntity<DeviceSubscriptionDto> resumeSubscription(@PathVariable String subscriptionId) {
        try {
            Subscription subscription = Subscription.retrieve(subscriptionId);
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(false)
                    .build();
            Subscription updated = subscription.update(params);
            return ResponseEntity.ok(DeviceSubscriptionDto.from(updated));
        } catch (StripeException e) {
            System.out.println("Unable to update Stripe subscription!");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/subscription/{subscriptionId}/upgrade")
    public ResponseEntity<DeviceSubscriptionDto> upgradeSubscription(@PathVariable String subscriptionId) {
        try {
            Subscription subscription = Subscription.retrieve(subscriptionId);
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(false)
                    .addItem(
                            SubscriptionUpdateParams.Item.builder()
                                    .setId(subscription.getItems().getData().getFirst().getId())
                                    .setPrice(yearlyPriceId)
                                    .build()
                    )
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .build();
            Subscription updatedSubscription = subscription.update(params);
            return ResponseEntity.ok(DeviceSubscriptionDto.from(updatedSubscription));
        } catch (StripeException e) {
            System.out.println("Unable to update Stripe subscription!");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private String getPriceId(SubscriptionInterval subscriptionInterval) {
        return switch (subscriptionInterval) {
            case year -> yearlyPriceId;
            case month -> monthlyPriceId;
        };
    }
}
