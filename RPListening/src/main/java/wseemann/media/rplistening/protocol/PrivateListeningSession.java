/*
 * RPListening: An Open Source desktop client for Roku private listening.
 *
 * Copyright (C) 2021 William Seemann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package wseemann.media.rplistening.protocol;

import java.util.*;

import com.jaku.api.QueryRequests;
import com.jaku.model.Device;

import wseemann.media.rplistening.protocol.rtcp.RTCPThreadHandler;
import wseemann.media.rplistening.protocol.rtp.RTPThreadHandler;
import wseemann.media.rplistening.utils.Constants;
import wseemann.media.rplistening.utils.Log;
import wseemann.media.rplistening.utils.Process;
import wseemann.media.rplistening.utils.ShellCommand;
import wseemann.media.rplistening.websocket.RokuWebSocketListener;
import wseemann.media.rplistening.websocket.WebSocketConnectionImpl;

import java.io.IOException;
import java.net.*;

/**
 * This class maintains session related information and provides startup
 * functions.
 */

public class PrivateListeningSession {

	private static final String TAG = "Session";

	/**
	 * Bandwidth Available to the session.
	 *
	 */
	private final double bandwidth;

	/**
	 * Payload type for this session.
	 *
	 */
	private static byte payloadType;

	/**
	 * Synchronization Source identifier for this source.
	 *
	 */
	public static long SSRC;

	/**
	 * Total Number of RTP data packets sent out by this source since starting
	 * transmission.
	 *
	 */
	public static long packetCount;

	/**
	 * Total Number of payload octets (i.e not including header or padding) sent out
	 * by this source since starting transmission.
	 *
	 */
	public static long octetCount;

	/**
	 * Reference to the RTP Sender and Receiver Thread.
	 *
	 */
	public static RTPThreadHandler m_RTPHandler;

	/**
	 * Reference to the RTCP Sender and Receiver Thread handler.
	 *
	 */
	public static RTCPThreadHandler m_RTCPHandler;

	/**
	 * Initialize the Random Number Generator.
	 *
	 */
	private static final Random rnd = new Random();

	/**
	 * RTCP Related state variables. (Sec. 6.3 draft-ietf-avt-rtp-new.ps)
	 */

	/**
	 * The last time an RTCP packet was transmitted.
	 */
	public static double TimeOfLastRTCPSent = 0;

	/**
	 * The current time.
	 */
	public static double tc = 0;

	/**
	 * The next scheduled transmission time of an RTCP packet.
	 */
	public static double tn = 0;

	/**
	 * The estimated number of session members at time tp.
	 */
	public static int pmembers = 0;

	/**
	 * The target RTCP bandwidth, i.e., the total bandwidth that will be used for
	 * RTCP packets by all members of this session, in octets per second. This
	 * should be 5 parameter supplied to the application at startup.
	 */
	public static double rtcp_bw = 0;

	/**
	 * Flag that is true if the application has sent data since the 2nd previous
	 * RTCP report was transmitted.
	 */
	public static boolean we_sent = false;

	/**
	 * The average RTCP packet size sent by this user.
	 */
	public static double avg_rtcp_size = 0;

	/**
	 * Flag that is true if the application has not yet sent an RTCP packet.
	 */
	public static boolean initial = true;

	/**
	 * Average size of the packet constructed by the application
	 */
	public static double avg_pkt_sz = 0;

	/**
	 * True if session instantiator requested a close.
	 */
	public static boolean IsByeRequested = false;

	/**
	 * Deterministic time interval for next RTCP transmission.
	 */
	public static double Td = 0;

	/**
	 * Ramdomized time interval for next RTCP transmission.
	 */
	public static double T = 0;

	/**
	 * Time this source last sent an RTP Packet
	 */
	public static double timeOfLastRTPSent = 0;

	/**
	 * A hastable that stores all the sources subscribed to this multicast group
	 */
	private static Hashtable<Long, Source> sourceMap;

	private static WebSocketConnectionImpl webSocketConnection;
	private static PrivateListeningSession session;
	private static Process ffplayProcess;

	/**
	 * Requires CNAME and session bandwidth. Initializes the SSRC to a randomly generated number.
	 *
	 * @param MulticastGroupIPAddress Dotted decimal representation of the Multicast
	 *                                group IP address.
	 * @param MulticastGroupPort      Port number of the Multcast group.
	 * @param RTCPGroupPort           Port on which the session will receive ( and
	 *                                send to ) the RTCP packets.
	 * @param RTPSendFromPort         Local port from which the RTP packets are sent
	 *                                out (must be different from
	 *                                MulticastGroupPort).
	 * @param RTCPSendFromPort        Local port from which the RTCP packets are
	 *                                sent out (must be different from
	 *                                RTCPGroupPort).
	 * @param bandwidth               Bandwidth available to the session.
	 */
	public static void connect(String rokuIPAddress, ConnectionListener listener) {
		try {
			String rokuAddress = "http://" + rokuIPAddress + ":" + Constants.ROKU_ECP_PORT;

			String hostAddress = InetAddress.getLocalHost().getHostAddress();

			supportsPrivateListening(rokuAddress, listener);

			webSocketConnection = new WebSocketConnectionImpl(rokuAddress,
					new RokuWebSocketListener() {

				@Override
				public void onAuthSuccess() {
					Log.d(TAG, "onAuthSuccess!");
					webSocketConnection.setAudioOutput(hostAddress + ":" + Constants.RTP_PORT);
				}

				@Override
				public void onSetAudioOutput() {
					Log.d(TAG, "onSetAudioOutput!");

					session = new PrivateListeningSession(
							rokuIPAddress,
							hostAddress,
							Constants.RTP_PORT,
							Constants.RTCP_PORT,
							Constants.RTP_PORT,
							10000
							);
					session.setPayloadType(Constants.RTP_PAYLOAD_TYPE);
					session.startRTPReceiverThread();
					session.startAudioDecoder();
					listener.onConnected(session);
				}

				@Override
				public void onAuthFailed() {
					listener.onFailure(new Exception("Auth failure"));
				}
			});

			webSocketConnection.connect();
		} catch (IOException e) {
			listener.onFailure(e);
		}
	}

	public static void disconnect(PrivateListeningSession session) {
		if (session != null) {
			webSocketConnection.disconnect();
			session.stopRTCPSenderThread();
			session.stopRTPReceiverThread();
			session.stopAudioDecoder();
		}
	}

	public static void setDebugMode(boolean isDebugMode) {
		Log.suppressLogs = !isDebugMode;
	}

	private static void supportsPrivateListening(String rokuAddress, ConnectionListener listener) throws IOException {
		Device device = QueryRequests.queryDeviceInfo(rokuAddress);

		String supportsPrivateListening = device.getSupportsPrivateListening();

		if (!Boolean.parseBoolean(supportsPrivateListening)) {
			listener.onFailure(new Exception("Device does not support private listening"));
		}
	}

	/**
	 * The only constructor. Requires CNAME and session bandwidth. Initializes the
	 * SSRC to a randomly generated number.
	 *
	 * @param MulticastGroupIPAddress Dotted decimal representation of the Multicast
	 *                                group IP address.
	 * @param MulticastGroupPort      Port number of the Multcast group.
	 * @param RTCPGroupPort           Port on which the session will receive ( and
	 *                                send to ) the RTCP packets.
	 * @param RTPSendFromPort         Local port from which the RTP packets are sent
	 *                                out (must be different from
	 *                                MulticastGroupPort).
	 * @param RTCPSendFromPort        Local port from which the RTCP packets are
	 *                                sent out (must be different from
	 *                                RTCPGroupPort).
	 * @param bandwidth               Bandwidth available to the session.
	 */
	private PrivateListeningSession(
	        String MulticastGroupIPAddress,
            String loopbackIPAddress,
            int MulticastGroupPort,
            int RTCPGroupPort,
			int RTCPSendFromPort,
            double bandwidth
    ) {
		this.bandwidth = bandwidth;

		sourceMap = new Hashtable<>();

		InetAddress inetAddress = GetInetAddress(MulticastGroupIPAddress);
		InetAddress loopbackAddress = GetInetAddress(loopbackIPAddress);

		// Create a new RTP Handler thread (but do not start it yet)
		m_RTPHandler = new RTPThreadHandler(inetAddress, loopbackAddress, MulticastGroupPort);

		// Create a new RTCP Handler thread (but do not start it yet)
		// Set the sendto and recvfrom ports
		m_RTCPHandler = new RTCPThreadHandler(inetAddress, RTCPSendFromPort, RTCPGroupPort);

		// Initilize session level variables
		Initialize();

		Log.d(TAG, "SSRC: 0x" + Long.toHexString(SSRC));

	}

	/**
	 * Set the Payload type.
	 */
	public synchronized void setPayloadType(int payloadType) {
		this.payloadType = (byte) payloadType;
	}

	/**
	 * Get the Payload type.
	 */
	public synchronized static byte getPayloadType() {
		return payloadType;
	}

	/**
	 * Starts the RTP Receiver thread.
	 */
	public synchronized void startRTPReceiverThread() {
		m_RTPHandler.startRTPReceiverThread();
	}

	/**
	 * Stops the RTP Receiver thread.
	 */
	public synchronized void stopRTPReceiverThread() {
		m_RTPHandler.stopRTPReceiverThread();
	}

	/**
	 * Stops the RTCP Sender thread
	 *
	 */
	public synchronized void stopRTCPSenderThread() {
		m_RTCPHandler.stopRTCPSenderThread();
	}

	/**
	 * Starts the audio decoder
	 *
	 */
	public synchronized void startAudioDecoder() {
		ShellCommand shellCommand = new ShellCommand();
		ffplayProcess = shellCommand.execute("");
	}

	/**
	 * Stop the audio decoder
	 *
	 */
	public synchronized void stopAudioDecoder() {
		ffplayProcess.destroy();
	}

	/**
	 * Retrieves a source object from the map using the given SSRC as a key. If the
	 * source does not exist, it is added to the map and newly created source object
	 * is returned.
	 *
	 * @param keySSRC The SSRC to look for in the map, if it doesn't exist a new
	 *                source is created and returned.
	 * @return The source corresponding the given SSRC, this source may be extracted
	 *         from the map or newly created.
	 */
	public synchronized static Source GetSource(long keySSRC) {
		Source s;

		if (sourceMap.containsKey(new Long(keySSRC)))
			s = (Source) sourceMap.get(new Long(keySSRC));
		else // source doesn't exist in the map, add it
		{
			s = new Source(keySSRC);
			AddSource(keySSRC, s);

		}

		return s;
	}

	/**
	 * Removes a source from the map.
	 *
     * @param sourceSSRC The source with this SSRC has to be removed.
     */
	public synchronized static void RemoveSource(long sourceSSRC) {
		if (sourceMap.get(sourceSSRC) != null) {
			sourceMap.remove(sourceSSRC);
			Log.d(TAG, "Removing Source : " + "SSRC = 0x" + Integer.toHexString((int) sourceSSRC));
			Log.d(TAG, "No. of members" + GetNumberOfMembers());
			Log.d(TAG, "No. of senders" + GetNumberOfActiveSenders());
		} else {
			Log.d(TAG, "Trying to remove SSRC which doesnt exist :" + sourceSSRC);
		}
    }

	/**
	 * Creates and return a InetAddress object.
	 *
	 * @param MulticastAddress Dotted decimal IP address from which a <b>
	 *                         InetAddress </b> object will be created and returned.
	 * @return Desired InetAddress object.
	 */
	public synchronized static InetAddress GetInetAddress(String MulticastAddress) {
		InetAddress ia = null;
		try {
			ia = InetAddress.getByName(MulticastAddress);
		} catch (Exception ex) {
			System.err.println(ex.getMessage());
			System.exit(1);
		}

		return (ia);
	}

	/**
	 * Returns the number of members.
	 *
	 * @return Total number of members.
	 */
	public static int GetNumberOfMembers() {
		// Go through the map and return the total number of sources.
		return sourceMap.size();
	}

	/**
	 * Returns the number of active senders.
	 *
	 * @return Number of senders.
	 */
	public static int GetNumberOfActiveSenders() {
		// Hasttable
		Enumeration<Source> SourceCollection = GetSources();
		int i = 0;
		while (SourceCollection.hasMoreElements()) {
			Source s = SourceCollection.nextElement();

			if (s.isActiveSender()) {
				i++;
			}
		}
		return (i);
	}

	/**
	 * Calculates the next interval, sets the T and Td class level static variables.
	 *
	 * Method to calculate the RTCP transmission interval T. from Section A7
	 * Computing the RTCP Transmission Interval ( with minor modifications )
	 *
     */
	public static synchronized void CalculateInterval() {
		// Update T and Td ( same as rtcp_interval() function in rfc.

		int members = GetNumberOfMembers();
		int senders = GetNumberOfActiveSenders();

		/*
		 * Minimum average time between RTCP packets from this site (in seconds). This
		 * time prevents the reports from `clumping' when sessions are small and the law
		 * of large numbers isn't helping to smooth out the traffic. It also keeps the
		 * report interval from becoming ridiculously small during transient outages
		 * like a network partition.
		 */
		final long RTCP_MIN_TIME = (long) 5.;
		/*
		 * Fraction of the RTCP bandwidth to be shared among active senders. (This
		 * fraction was chosen so that in a typical session with one or two active
		 * senders, the computed report time would be roughly equal to the minimum
		 * report time so that we don't unnecessarily slow down receiver reports.) The
		 * receiver fraction must be 1 � the sender fraction.
		 */
		final double RTCP_SENDER_BW_FRACTION = 0.25;
		final double RTCP_RCVR_BW_FRACTION = (1 - RTCP_SENDER_BW_FRACTION);
		double t; /* interval */
		double rtcp_min_time = RTCP_MIN_TIME;
		double n; /* no. of members for computation */
		/*
		 * Very first call at application start�up uses half the min delay for quicker
		 * notification while still allowing some time before reporting for
		 * randomization and to learn about other sources so the report interval will
		 * converge to the correct interval more quickly.
		 */
		if (initial) {
			rtcp_min_time /= 2;
		}
		/*
		 * If there were active senders, give them at least a minimum share of the RTCP
		 * bandwidth. Otherwise all participants share the RTCP bandwidth equally.
		 */

		n = members;

		if (senders > 0 && senders < members * RTCP_SENDER_BW_FRACTION) {
			if (GetMySource().isActiveSender()) {
				rtcp_bw *= RTCP_SENDER_BW_FRACTION;
				n = senders;
			} else {
				rtcp_bw *= RTCP_RCVR_BW_FRACTION;
				n -= senders;
			}
		}

		/*
		 * The effective number of sites times the average packet size is the total
		 * number of octets sent when each site sends a report. Dividing this by the
		 * effective bandwidth gives the time interval over which those packets must be
		 * sent in order to meet the bandwidth target, with a minimum enforced. In that
		 * time interval we send one report so this time is also our average time
		 * between reports.
		 */
		t = avg_rtcp_size * n / rtcp_bw;
		if (t < rtcp_min_time)
			t = rtcp_min_time;
		/*
		 * To avoid traffic bursts from unintended synchronization with other sites, we
		 * then pick our actual next report interval as a random number uniformly
		 * distributed between 0.5*t and 1.5*t.
		 */
		double noise = (rnd.nextDouble() + 0.5);

		PrivateListeningSession.Td = t;
		PrivateListeningSession.T = t * noise;
    }

	/**
	 * Initialize the Session level variables.
	 *
	 */
	public void Initialize() {
		TimeOfLastRTCPSent = CurrentTime();
		tc = CurrentTime();
		pmembers = 1;
		we_sent = true;
		rtcp_bw = 0.05 * bandwidth;
		initial = true;
		avg_pkt_sz = 0; // TODO: Set the the size of the first packet generated by app
		SSRC = Math.abs(rnd.nextInt());

		packetCount = 0;
		octetCount = 0;

		// Set the next transmission time to the interval
		tn = T;

		// Add self as a source object into the SSRC table maintained by the session
		PrivateListeningSession.AddSource(PrivateListeningSession.SSRC, new Source(PrivateListeningSession.SSRC));

    }

	/**
	 * Returns a self source object.
	 *
	 * @return My source object.
	 */
	public static synchronized Source GetMySource() {
		return sourceMap.get(SSRC);

	}

	/**
	 * Adds an SSRC into the table, if SSRC Exists, error code -1 is returned.
	 *
	 * @param newSSRC SSRC of the source being added.
	 * @param src     Source object of the source being added.
     */

	public static void AddSource(long newSSRC, Source src) {

		if (sourceMap.containsKey(newSSRC)) {
        } else {
			sourceMap.put(newSSRC, src);
			Log.d(TAG, "Adding Source : " + "SSRC = 0x" + Integer.toHexString((int) newSSRC));
			Log.d(TAG, "No. of members" + GetNumberOfMembers());
			Log.d(TAG, "No. of senders" + GetNumberOfActiveSenders());

		}
    }

    /**
	 * Return an iterable enumeration of all sources contained in the Map.
	 *
	 * @return Enumeration of all the sources (members).
	 */
	public static synchronized Enumeration<Source> GetSources() {
		return sourceMap.elements();
	}

	/**
	 * Returns current time from the Date().getTime() function.
	 *
	 * @return The current time.
	 */
	public static long CurrentTime() {
		tc = (new Date()).getTime();
		return (long) tc;
	}

	/**
	 * Function removes all sources from the members table (except self). Returns
	 * number of sources removed.
	 *
     */
	public static synchronized void RemoveAllSources() {
		Enumeration<Source> SourceCollection = GetSources();

        while (SourceCollection.hasMoreElements()) {
			Source s = SourceCollection.nextElement();

			if (s.getSsrc() != SSRC) {
				RemoveSource(s.getSsrc());
            }
		}

		pmembers = 1;
		CalculateInterval();

    }
}
