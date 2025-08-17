package com.modernchat.event;

import com.modernchat.feature.ChatFeature;
import lombok.Value;

@Value
public class FeatureStartedEvent
{
    ChatFeature<?> feature;
}
