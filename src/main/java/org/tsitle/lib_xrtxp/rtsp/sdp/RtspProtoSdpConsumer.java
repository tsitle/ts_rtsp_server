package org.tsitle.lib_xrtxp.rtsp.sdp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdp;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSdpException;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoSdpConsumerInterface;

public final class RtspProtoSdpConsumer implements RtspProtoSdpConsumerInterface {

	@Override
	public void parseSdpFromDescribe(@NonNull RtspProtoDataCntSdp inputSdp) throws RtspProtoSdpException {
		// @TODO
	}

	@Override
	public void parseUpdatedSdpFromAnnounce(@NonNull RtspProtoDataCntSdp inputSdp) throws RtspProtoSdpException {
		// @TODO
	}

}
