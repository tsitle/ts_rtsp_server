package org.tsitle.lib.rtsp.proto.sdp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.data_rr.RtspProtoDataCntSdp;
import org.tsitle.lib.rtsp.proto.exceptions.RtspProtoSdpException;
import org.tsitle.lib.rtsp.proto.interfaces.RtspProtoSdpConsumerInterface;

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
