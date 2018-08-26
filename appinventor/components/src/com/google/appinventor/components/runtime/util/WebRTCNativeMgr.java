// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2018 MIT, All rights reserved
// THIS IS UNRELEASED CODE WHICH CONTAINS PROPRIETARY SECRETS

package com.google.appinventor.components.runtime.util;

import android.content.Context;
import android.util.Log;

import java.util.Collections;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceGatheringState;
import org.webrtc.PeerConnection.Observer;
import org.webrtc.PeerConnection.SignalingState;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;

public class WebRTCNativeMgr {

  private static final String LOG_TAG = "AppInvWebRTC";
  private PeerConnection peerConnection;

  public WebRTCNativeMgr() {
  }

  public void test1(Context context) {

    /* Initialize WebRTC globally */
    PeerConnectionFactory.initializeAndroidGlobals(context, false);
    /* Setup factory options */
    PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
    /* Create the factory */
    PeerConnectionFactory factory = new PeerConnectionFactory(options);
    /* Create out list of iceServers (only one for now, note this information is secret!) */
    PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder("turn:turn.appinventor.mit.edu:3478")
      .setUsername("oh")
      .setPassword("boy")
      .createIceServer();
    /* Create the Observer which will be called when events heppen */

    SdpObserver sdpObserver = new SdpObserver() {
        public void onCreateFailure(String str) {
        }

        public void onCreateSuccess(SessionDescription sessionDescription) {
          Log.d(LOG_TAG, "sdp.type = " + sessionDescription.type.canonicalForm());
          Log.d(LOG_TAG, "sdp.description = " + sessionDescription.description);
          DataChannel.Init init = new DataChannel.Init();
          peerConnection.createDataChannel("data", init);
        }

        public void onSetFailure(String str) {
        }

        public void onSetSuccess() {
        }
      };

    Observer observer = new Observer() {
        public void onAddStream(MediaStream mediaStream) {
        }

        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreamArr) {
        }

        public void onDataChannel(DataChannel dataChannel) {
        }

        public void onIceCandidate(IceCandidate iceCandidate) {
          Log.d(LOG_TAG, "IceCandidate = " + iceCandidate.toString());
        }

        public void onIceCandidatesRemoved(IceCandidate[] iceCandidateArr) {
        }

        public void onIceConnectionChange(IceConnectionState iceConnectionState) {
        }

        public void onIceConnectionReceivingChange(boolean z) {
        }

        public void onIceGatheringChange(IceGatheringState iceGatheringState) {
        }

        public void onRemoveStream(MediaStream mediaStream) {
        }

        public void onRenegotiationNeeded() {
        }

        public void onSignalingChange(SignalingState signalingState) {
        }
      };


    peerConnection = factory.createPeerConnection(Collections.singletonList(iceServer), new MediaConstraints(),
                                                                 observer);

    peerConnection.createOffer(sdpObserver, new MediaConstraints()); // Let's see what happens :-)


    // /* Initialize WebRTC globally */
    // PeerConnectionFactory.InitializationOptions initializationOptions =
    //   PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions();
    // PeerConnectionFactory.initialize(initializationOptions);
    // /* Setup factory options */
    // PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
    // /* Create the factory */
    // PeerConnectionFactory factory = PeerConnectionFactory.builder()
    //   .setOptions(options).createPeerConnectionFactory();
    // /* Create out list of iceServers (only one for now, note this information is secret!) */
    // PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder("turn:turn.appinventor.mit.edu:3478")
    //   .setUsername("oh")
    //   .setPassword("boy")
    //   .createIceServer();
    // /* Create the Observer which will be called when events heppen */
    // Observer observer = new Observer() {

    //         public void onAddStream(MediaStream mediaStream) {
    //         }

    //         public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreamArr) {
    //         }

    //         public void onDataChannel(DataChannel dataChannel) {
    //         }

    //         public void onIceCandidate(IceCandidate iceCandidate) {
    //         }

    //         public void onIceCandidatesRemoved(IceCandidate[] iceCandidateArr) {
    //         }

    //         public void onIceConnectionChange(IceConnectionState iceConnectionState) {
    //         }

    //         public void onIceConnectionReceivingChange(boolean z) {
    //         }

    //         public void onIceGatheringChange(IceGatheringState iceGatheringState) {
    //         }

    //         public void onRemoveStream(MediaStream mediaStream) {
    //         }

    //         public void onRenegotiationNeeded() {
    //         }

    //         public void onSignalingChange(SignalingState signalingState) {
    //         }

    //         public void onTrack(RtpTransceiver transceiver) {
    //         }
    //     };

    // PeerConnection peerConnection = factory.createPeerConnection(Collections.singletonList(iceServer),
    //   observer);

  }

}
