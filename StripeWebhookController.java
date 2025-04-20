package com.example.api.controller;

import com.example.api.service.DeviceSubscriptionService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("public/stripe/webhook")
@RequiredArgsConstructor
public class StripeWebhookController {

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    private final DeviceSubscriptionService deviceSubscriptionService;

    // TODO MOVE
    // TODO make all requests idempotent, return 2XX code ONLY when database is in sync with the data from Stripe
    // TODO maybe listen to invoice paid and invoice payment failed and just add logs
    @PostMapping
    @Transactional
    public ResponseEntity<String> handleStripeEvent(@RequestBody String payload,
                                                    @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        // TODO maybe handle invoice failed/payment failed event because subscription might cancel because of that?
        // can stripe send email on my behalf in this case???
        switch (event.getType()) {
            case "customer.subscription.created":
                Subscription subscriptionCreated = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                deviceSubscriptionService.handleSubscriptionCreated(subscriptionCreated);
                // TODO is this the time to set new device limits?
                break;

            case "customer.subscription.updated":
                Subscription subscriptionUpdate = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                deviceSubscriptionService.handleSubscriptionUpdated(subscriptionUpdate);
                break;
            case "customer.subscription.deleted":
                Subscription subscriptionDeleted = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                deviceSubscriptionService.handleSubscriptionCanceled(subscriptionDeleted);
                // TODO revoke access
                break;
        }

        return ResponseEntity.ok("Event received");
    }
}
