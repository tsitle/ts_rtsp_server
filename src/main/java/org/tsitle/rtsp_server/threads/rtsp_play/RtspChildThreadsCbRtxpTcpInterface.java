package org.tsitle.rtsp_server.threads.rtsp_play;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;

import java.time.Instant;

/**
 * Callback interface for RTSP child threads.
 */
public interface RtspChildThreadsCbRtxpTcpInterface {

	boolean isTcpConnectionAlive();

	// -----------------------------------------------------------------------------------------------------------------

	void cbSendRtpBinaryOverTcp(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Wait until an RTCP packet is available for reading or the timeout has elapsed.
	 * @param channNr TCP channel number
	 * @param timeoutMs Max. time to wait in milliseconds
	 * @return true if an RTCP packet is available
	 */
	boolean cbWaitUntilCanReadRtcpOverTcp(@NonNull RtspProtoTcpChannelNr channNr, long timeoutMs)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;
	boolean cbReadRtcpBinaryOverTcp(@NonNull BufferExt buf, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;
	void cbNotifyRcvdRtcpRrPacketOverTcp(@NonNull Instant time);
	void cbSendRtcpBinaryOverTcp(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

}
