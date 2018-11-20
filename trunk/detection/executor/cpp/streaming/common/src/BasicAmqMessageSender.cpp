/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

#include <chrono>

#include <activemq/library/ActiveMQCPP.h>
#include <activemq/core/ActiveMQConnectionFactory.h>

#include "detection.pb.h"

#include "BasicAmqMessageSender.h"
#include "ExecutorErrors.h"


using org::mitre::mpf::wfm::buffers::StreamingDetectionResponse;

using namespace MPF;
using namespace COMPONENT;

BasicAmqMessageSender::BasicAmqMessageSender(const JobSettings &job_settings,
                                             std::shared_ptr<cms::Connection> connection_ptr)
        : job_id_(job_settings.job_id)
        , segment_size_(job_settings.segment_size)
        , connection_(connection_ptr)
        , session_(connection_->createSession())
        , segment_ready_producer_(CreateProducer(job_settings.segment_ready_queue, *session_))
        , frame_ready_producer_(CreateProducer(job_settings.frame_ready_queue, *session_))
        , job_status_producer_(CreateProducer(job_settings.job_status_queue, *session_))
        , activity_alert_producer_(CreateProducer(job_settings.activity_alert_queue, *session_))
        , summary_report_producer_(CreateProducer(job_settings.summary_report_queue, *session_))
        , release_frame_producer_(CreateProducer(job_settings.release_frame_queue, *session_)) {}


 std::unique_ptr<cms::MessageProducer> BasicAmqMessageSender::CreateProducer(const std::string &queue_name,
                                                                             cms::Session &session) {
     std::unique_ptr<cms::Queue> queue(session.createQueue(queue_name));
     return std::unique_ptr<cms::MessageProducer>(session.createProducer(queue.get()));
 }


 void BasicAmqMessageSender::SendSegmentReady(const int segment_number,
                                              const int frame_width,
                                              const int frame_height,
                                              const int cvType,
                                              const int bytes_per_pixel) {
     std::unique_ptr<cms::Message> message(session_->createMessage());
     message->setLongProperty("JOB_ID", job_id_);
     message->setIntProperty("SEGMENT_NUMBER", segment_number);
     message->setIntProperty("FRAME_WIDTH", frame_width);
     message->setIntProperty("FRAME_HEIGHT", frame_height);
     message->setIntProperty("FRAME_DATA_TYPE", cvType);
     message->setIntProperty("FRAME_BYTES_PER_PIXEL", bytes_per_pixel);
     segment_ready_producer_->send(message.get());
 }

 void BasicAmqMessageSender::SendFrameReady(const int segment_number,
                     const int frame_index,
                     const long frame_timestamp) {
     std::unique_ptr<cms::Message> message(session_->createMessage());
     message->setLongProperty("JOB_ID", job_id_);
     message->setIntProperty("SEGMENT_NUMBER", segment_number);
     message->setIntProperty("FRAME_INDEX", frame_index);
     message->setLongProperty("FRAME_READ_TIMESTAMP", frame_timestamp);
     frame_ready_producer_->send(message.get());
}

void BasicAmqMessageSender::SendReleaseFrame(const int frame_index) {
    std::unique_ptr<cms::Message> message(session_->createMessage());
    message->setLongProperty("JOB_ID", job_id_);
    message->setIntProperty("FRAME_INDEX", frame_index);
    release_frame_producer_->send(message.get());
}
    


void BasicAmqMessageSender::SendJobStatus(const std::string &job_status, long timestamp) {
    std::unique_ptr<cms::Message> message(session_->createMessage());
    message->setLongProperty("JOB_ID", job_id_);
    message->setStringProperty("JOB_STATUS", job_status);
    message->setLongProperty("STATUS_CHANGE_TIMESTAMP", timestamp);
    job_status_producer_->send(message.get());
}


void BasicAmqMessageSender::SendStallAlert(long timestamp) {
    SendJobStatus("STALLED", timestamp);
}

void BasicAmqMessageSender::SendInProgressNotification(long timestamp) {
    SendJobStatus("IN_PROGRESS", timestamp);
}


void BasicAmqMessageSender::SendActivityAlert(int frame_index, long timestamp) {
    std::unique_ptr<cms::Message> message(session_->createMessage());
    message->setLongProperty("JOB_ID", job_id_);
    message->setIntProperty("FRAME_INDEX", frame_index);
    message->setLongProperty("ACTIVITY_DETECTION_TIMESTAMP", timestamp);
    activity_alert_producer_->send(message.get());
}


void BasicAmqMessageSender::SendSummaryReport(const int frame_index,
                                              const int segment_number,
                                              const std::string &detection_type,
                                              const std::vector<MPFVideoTrack> &tracks,
                                              const std::unordered_map<int, long> &frame_timestamps,
                                              const std::string &error_message) {
    StreamingDetectionResponse protobuf_response;

    protobuf_response.set_segment_number(segment_number);
    protobuf_response.set_segment_start_frame(segment_size_ * segment_number);
    protobuf_response.set_segment_stop_frame(frame_index);
    protobuf_response.set_detection_type(detection_type);
    if (!error_message.empty())  {
        protobuf_response.set_error(error_message);
    }

    for (const auto &track : tracks) {
        auto protobuf_track = protobuf_response.add_video_tracks();
        protobuf_track->set_start_frame(track.start_frame);
        protobuf_track->set_start_time(frame_timestamps.at(track.start_frame));

        protobuf_track->set_stop_frame(track.stop_frame);
        protobuf_track->set_stop_time(frame_timestamps.at(track.stop_frame));

        protobuf_track->set_confidence(track.confidence);

        for (const auto &property : track.detection_properties) {
            auto protobuf_property = protobuf_track->add_detection_properties();
            protobuf_property->set_key(property.first);
            protobuf_property->set_value(property.second);
        }

        for (const auto &frame_location_pair : track.frame_locations) {
            auto protobuf_detection = protobuf_track->add_detections();
            protobuf_detection->set_frame_number(frame_location_pair.first);
            protobuf_detection->set_time(frame_timestamps.at(frame_location_pair.first));

            const auto &frame_location = frame_location_pair.second;
            protobuf_detection->set_x_left_upper(frame_location.x_left_upper);
            protobuf_detection->set_y_left_upper(frame_location.y_left_upper);
            protobuf_detection->set_width(frame_location.width);
            protobuf_detection->set_height(frame_location.height);
            protobuf_detection->set_confidence(frame_location.confidence);
            for (const auto &property : frame_location.detection_properties) {
                auto protobuf_property = protobuf_detection->add_detection_properties();
                protobuf_property->set_key(property.first);
                protobuf_property->set_value(property.second);
            }
        }
    }

    int proto_bytes_size = protobuf_response.ByteSize();
    std::unique_ptr<uchar[]> proto_bytes(new uchar[proto_bytes_size]);
    protobuf_response.SerializeWithCachedSizesToArray(proto_bytes.get());

    std::unique_ptr<cms::BytesMessage> message(session_->createBytesMessage(proto_bytes.get(), proto_bytes_size));
    message->setLongProperty("JOB_ID", job_id_);

    summary_report_producer_->send(message.get());
}