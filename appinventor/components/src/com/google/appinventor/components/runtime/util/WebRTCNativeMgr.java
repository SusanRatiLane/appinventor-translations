// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2018 MIT, All rights reserved
// THIS IS UNRELEASED CODE WHICH CONTAINS PROPRIETARY SECRETS

package com.google.appinventor.components.runtime.util;

import android.content.Context;
import android.util.Log;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import java.util.Collections;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.TreeSet;

public class WebRTCNativeMgr {

  private static final String LOG_TAG = "AppInvWebRTC";
  private PeerConnection peerConnection;
  /* We use a single threaded executor to read from the rendezvous server */
  private volatile ExecutorService background = Executors.newSingleThreadExecutor();
  /* We need to keep track of whether or not we have processed an element */
  /* Received from the rendezvous server. */
  private TreeSet<String> seenNonces = new TreeSet();
  private boolean haveOffer = false;

  Timer timer = new Timer();

  SdpObserver sdpObserver = new SdpObserver() {
      public void onCreateFailure(String str) {
      }

      public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d(LOG_TAG, "sdp.type = " + sessionDescription.type.canonicalForm());
        Log.d(LOG_TAG, "sdp.description = " + sessionDescription.description);
        DataChannel.Init init = new DataChannel.Init();
        if (sessionDescription.type == SessionDescription.Type.OFFER) {
          peerConnection.setRemoteDescription(sdpObserver, sessionDescription);
        } else if (sessionDescription.type == SessionDescription.Type.ANSWER) {
          peerConnection.setLocalDescription(sdpObserver, sessionDescription);
          /* Send to peer */
        }
        Log.d(LOG_TAG, "About to call create data connection");
        peerConnection.createDataChannel("data", init);
        Log.d(LOG_TAG, "createDataChannel returned");
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
        Log.d(LOG_TAG, "Have Data Channel!");
      }

      public void onIceCandidate(IceCandidate iceCandidate) {
        Log.d(LOG_TAG, "IceCandidate = " + iceCandidate.toString());
        /* Send to Peer */
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

  public WebRTCNativeMgr() {
  }

  public void initiate(Context context, String code) {

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

    peerConnection = factory.createPeerConnection(Collections.singletonList(iceServer), new MediaConstraints(),
                                                                 observer);
//    peerConnection.createOffer(sdpObserver, new MediaConstraints()); // Let's see what happens :-)
    seenNonces.clear();         // Reset in case we are called more then once...
    haveOffer = false;
    startPolling(code);

  }

  /*
   * startPolling: poll the Rendezvous server looking for the information via the
   * the provided code with "-s" appended (because we are the receiver, replmgr.js
   * is in the sender roll.
   */
  private void startPolling(final String code) {
    background.submit(new Runnable() {
        @Override public void run() {
          try {
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet("http://r2.appinventor.mit.edu/rendezvous2/" + code + "-s");
            HttpResponse response = client.execute(request);
            StringBuilder sb = new StringBuilder();

            BufferedReader rd = new BufferedReader
              (new InputStreamReader(
                response.getEntity().getContent()));
            String line = "";
            while ((line = rd.readLine()) != null) {
              sb.append(line);
            }
            Log.d(LOG_TAG, "response = " + sb.toString());
            JSONArray jsonArray = new JSONArray(sb.toString());
            Log.d(LOG_TAG, "jsonArray.length() = " + jsonArray.length());
            int i = 0;
            while (i < jsonArray.length()) {
              Log.d(LOG_TAG, "i = " + i);
              Log.d(LOG_TAG, "element = " + jsonArray.optString(i));
              JSONObject element = (JSONObject) jsonArray.get(i);
              if (!haveOffer) {
                if (!element.has("offer")) {
                  i++;
                  continue;
                }
                JSONObject offer = (JSONObject) element.get("offer");
                String sdp = offer.optString("sdp");
                String type = offer.optString("type");
                Log.d(LOG_TAG, "sdb = " + sdp);
                Log.d(LOG_TAG, "type = " + type);
                haveOffer = true;
                Log.d(LOG_TAG, "About to set remote offer");
                peerConnection.setRemoteDescription(sdpObserver,
                  new SessionDescription(SessionDescription.Type.OFFER, sdp));
                peerConnection.createAnswer(sdpObserver, new MediaConstraints());
                Log.d(LOG_TAG, "createAnswer returned");
                i = -1;
              } else {
                if (element.has("nonce")) {
                  String nonce = element.optString("nonce");
                  JSONObject candidate = (JSONObject) element.get("candidate");
                  if (candidate == null) {
                    Log.d(LOG_TAG, "Received a null candidate, skipping...");
                    i++;
                    continue;
                  }
                  String sdpcandidate = candidate.optString("candidate");
                  String sdpMid = candidate.optString("sdpMid");
                  int sdpMLineIndex = candidate.optInt("sdpMLineIndex");
                  Log.d(LOG_TAG, "candidate = " + sdpcandidate);
                  if (!seenNonces.contains(nonce)) {
                    seenNonces.add(nonce);
                    Log.d(LOG_TAG, "new nonce, about to add candidate!");
                    IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdpcandidate);
                    peerConnection.addIceCandidate(iceCandidate);
                    Log.d(LOG_TAG, "addIceCandidate returned");
                  }
                }
              }
              i++;
            }
            Log.d(LOG_TAG, "exited loop");
          } catch (IOException e) {
            Log.d(LOG_TAG, "Caught IOException: " + e.toString());
          } catch (JSONException e) {
            Log.d(LOG_TAG, "Caught JSONException: " + e.toString());
          } catch (Exception e) {
            Log.d(LOG_TAG, "Caught Exception: " + e.toString());
          }
          timer.schedule(new TimerTask() {
              @Override
              public void run() {
                WebRTCNativeMgr.this.startPolling(code);
              }
            }, 1000);
        }
      });
  }
}

