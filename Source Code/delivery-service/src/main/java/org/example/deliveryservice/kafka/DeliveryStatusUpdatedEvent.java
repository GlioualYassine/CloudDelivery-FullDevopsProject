package org.example.deliveryservice.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.deliveryservice.model.DeliveryStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStatusUpdatedEvent {
    private Long deliveryId;
    private Long orderId;
    private String clientEmail;
    private String driverName;
    private DeliveryStatus status;
    private String updatedAt;
}
