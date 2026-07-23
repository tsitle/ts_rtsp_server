package org.tsitle.rtsp_server.threads.dataprovider_es;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

interface PsParseAndConvertDataInterface<I extends CodecInfoInterface<I>> {

	@NonNull I parseAndConvertData(@NonNull BufferExt ioBuf) throws AvInvalidCodecDataException;

}
