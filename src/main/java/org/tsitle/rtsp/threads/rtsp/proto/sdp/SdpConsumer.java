package org.tsitle.rtsp.threads.rtsp.proto.sdp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntSdp;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspSdpException;

public final class SdpConsumer implements SdpConsumerInterface {

	@Override
	public void parseSdpFromDescribe(@NonNull RtspProtoDataCntSdp inputSdp) throws RtspSdpException {
		// @TODO
	}

	@Override
	public void parseUpdatedSdpFromAnnounce(@NonNull RtspProtoDataCntSdp inputSdp) throws RtspSdpException {
		// @TODO
	}

}
