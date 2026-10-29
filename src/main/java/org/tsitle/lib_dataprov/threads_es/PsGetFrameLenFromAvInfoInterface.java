package org.tsitle.lib_dataprov.threads_es;

import org.jspecify.annotations.NonNull;
import io.github.tsitle.lib_xrtxp.avdata.CodecInfoInterface;

interface PsGetFrameLenFromAvInfoInterface<I extends CodecInfoInterface<I>> {

	int getFrameLen(@NonNull I avInfo);

}
