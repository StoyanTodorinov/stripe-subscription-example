package com.example.api.service;

import com.example.api.entity.Device;
import com.example.api.entity.DeviceSubscription;
import com.example.api.enums.SubscriptionInterval;
import com.example.api.repository.DeviceSubscriptionRepository;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceSubscriptionService {

    private final DeviceService deviceService;
    private final DeviceSubscriptionRepository subscriptionRepository;

    // TODO what if subscription is not null? - TEST!
    // TODO what if there is another valid subscription??
    @Transactional
    public void handleSubscriptionCreated(Subscription subscription) {
        Device device = deviceService.fetchDevice(extractDeviceId(subscription));
        if (device.getSubscription() != null) {
            if (device.getSubscription().getStatus().equals("active")) {
                System.err.println("ERROR!!! - We already have an active subscription, now we add another one!");
            }
            updateSubscription(subscription, device);
        } else {
            newSubscription(subscription, device);
        }

        System.out.println("Subscription inserted!");
    }

    @Transactional
    public void handleSubscriptionUpdated(Subscription subscription) {
        Device device = deviceService.fetchDevice(extractDeviceId(subscription));
        updateSubscription(subscription, device);
        System.out.println("Subscription update handled!");
    }

    @Transactional
    public void handleSubscriptionCanceled(Subscription subscription) {
        Device device = deviceService.fetchDevice(extractDeviceId(subscription));
        updateSubscription(subscription, device);
        // TODO update device to set limit because subscription ended
        System.out.println("Subscription cancel handled!");
    }

    private void updateSubscription(Subscription subscription, Device device) {
        DeviceSubscription deviceSubscription = device.getSubscription();
        deviceSubscription.setSubscriptionId(subscription.getId());
        deviceSubscription.setStatus(subscription.getStatus());
        deviceSubscription.setStartDate(subscription.getStartDate());
        deviceSubscription.setCancelAt(subscription.getCancelAt());
        deviceSubscription.setCurrentPeriodEnd(subscription.getItems().getData().getFirst().getCurrentPeriodEnd());
        deviceSubscription.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());
        deviceSubscription.setEndedAt(subscription.getEndedAt());
        deviceSubscription.setSubscriptionInterval(SubscriptionInterval.valueOf(
                subscription.getItems().getData().getFirst().getPlan().getInterval())
        );
        subscriptionRepository.save(deviceSubscription);
    }

    private void newSubscription(Subscription subscription, Device device) {
        DeviceSubscription deviceSubscription = new DeviceSubscription();
        deviceSubscription.setId(UUID.randomUUID());
        deviceSubscription.setSubscriptionId(subscription.getId());
        deviceSubscription.setStatus(subscription.getStatus());
        deviceSubscription.setStartDate(subscription.getStartDate());
        deviceSubscription.setCancelAt(subscription.getCancelAt());
        deviceSubscription.setCurrentPeriodEnd(subscription.getItems().getData().getFirst().getCurrentPeriodEnd());
        deviceSubscription.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());
        deviceSubscription.setEndedAt(subscription.getEndedAt());
        deviceSubscription.setSubscriptionInterval(SubscriptionInterval.valueOf(
                subscription.getItems().getData().getFirst().getPlan().getInterval())
        );
        deviceSubscription.setDevice(device);
        subscriptionRepository.save(deviceSubscription);
    }

    private String extractDeviceId(Subscription subscription) {
        if (subscription != null) {
            Map<String, String> metadata = subscription.getMetadata();
            return metadata.get("deviceId");
        }

        // TODO throw or log an error log, this shouldn't happen!
        System.err.println("Subscription is NULL! ERROR!");
        return null;
    }
}
