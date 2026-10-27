package org.tsitle.lib_xrtxp.rtsp.tcpcomms;

import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;

interface TcpActivityInterface {

	void checkTcpActivityTimeout() throws TcpSocketActivityTimeoutException;

	void resetTcpActivityTimeoutTimer();

}
