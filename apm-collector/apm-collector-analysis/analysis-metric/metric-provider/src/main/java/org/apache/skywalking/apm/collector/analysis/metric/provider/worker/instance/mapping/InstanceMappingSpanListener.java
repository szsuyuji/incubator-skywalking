/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.mapping;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.SpanDecorator;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.EntrySpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.FirstSpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.SpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.SpanListenerFactory;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceMappingSpanListener implements FirstSpanListener, EntrySpanListener {

    private static final Logger logger = LoggerFactory.getLogger(InstanceMappingSpanListener.class);

    private List<InstanceMapping> instanceMappings = new LinkedList<>();
    private long timeBucket;

    @Override public boolean containsPoint(Point point) {
        return Point.First.equals(point) || Point.Entry.equals(point);
    }

    @Override public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        logger.debug("instance mapping listener parse reference");
        if (spanDecorator.getRefsCount() > 0) {
            for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
                InstanceMapping instanceMapping = new InstanceMapping();
                instanceMapping.setApplicationId(applicationId);
                instanceMapping.setInstanceId(instanceId);
                instanceMapping.setAddressId(spanDecorator.getRefs(i).getNetworkAddressId());
                String metricId = String.valueOf(instanceId) + Const.ID_SPLIT + String.valueOf(instanceMapping.getAddressId());
                instanceMapping.setMetricId(metricId);
                instanceMappings.add(instanceMapping);
            }
        }
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        if (spanDecorator.getStartTimeMinuteTimeBucket() == 0) {
            long startTimeMinuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
            spanDecorator.setStartTimeMinuteTimeBucket(startTimeMinuteTimeBucket);
        }
        timeBucket = spanDecorator.getStartTimeMinuteTimeBucket();
    }

    @Override public void build() {
        logger.debug("instance mapping listener build");
        Graph<InstanceMapping> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.INSTANCE_MAPPING_GRAPH_ID, InstanceMapping.class);
        instanceMappings.forEach(instanceMapping -> {
            instanceMapping.setId(timeBucket + Const.ID_SPLIT + instanceMapping.getMetricId());
            instanceMapping.setTimeBucket(timeBucket);
            logger.debug("push to instance mapping aggregation worker, id: {}", instanceMapping.getId());
            graph.start(instanceMapping);
        });
    }

    public static class Factory implements SpanListenerFactory {

        @GraphComputingMetric(name = "/segment/parse/createSpanListeners/instanceMappingSpanListener")
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new InstanceMappingSpanListener();
        }
    }
}
