#include <iostream>
#include <sstream>
#include <cstdlib>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <arpa/inet.h>

#define ERROR { \
	std::cerr << "Error on line " << __LINE__ << ": " << std::strerror(errno) << std::endl; \
	exit(1); \
}

void forward_sockets(int sock_from, int sock_to) {
	char buf[BUFSIZ];
	while (true) {
		int cnt_read = read(sock_from, buf, BUFSIZ);
		std::cerr << cnt_read << " bytes read." << std::endl;
		if (cnt_read <= 0) {
			shutdown(sock_to, 1);
			close(sock_from);
			close(sock_to);
			if (cnt_read == -1) ERROR;
			return;
		}
		int offset = 0;
		while (offset < cnt_read) {
			int cnt_written = write(sock_to, buf + offset, cnt_read - offset);
			std::cerr << cnt_written << " bytes writtern" << std::endl;
			if (cnt_written == -1) {
				close(sock_from);
				close(sock_to);
				ERROR;
			}
			offset += cnt_written;
		}
	}
}

void process_connection(int agent_sock) {
	sockaddr_in addr;
	addr.sin_family = AF_INET;
	addr.sin_addr.s_addr = inet_addr("127.0.0.1");
	socklen_t addr_len = sizeof(sockaddr_in);
	int app_listen_sock = socket(AF_INET, SOCK_STREAM, 0);
	if (app_listen_sock == -1) ERROR;
	if (bind(app_listen_sock, (sockaddr *)&addr, addr_len) == -1) ERROR;
	if (listen(app_listen_sock, 1) == -1) ERROR;
	if (getsockname(app_listen_sock, (sockaddr *)&addr, &addr_len) == -1) ERROR;
	std::stringstream cmd_ss;
	cmd_ss << "am broadcast -n org.ddosolitary.okcagent/.ProxyReceiver -a org.ddosolitary.okcagent.action.RUN_SSH_AGENT --ei org.ddosolitary.okcagent.extra.PROXY_PORT " << ntohs(addr.sin_port);
	system(cmd_ss.str().c_str());
	std::cerr << "Broadcast sent, waiting for the app." << std::endl;
	int app_sock = accept(app_listen_sock, nullptr, nullptr);
	if (app_sock == -1) ERROR;
	close(app_listen_sock);
	int pid = fork();
	if (pid == -1) ERROR;
	if (pid == 0) {
		std::cerr << "Starting forwarder." << std::endl;
		forward_sockets(agent_sock, app_sock);
		std::cerr << "Exiting forwarder." << std::endl;
		exit(0);
	}
	pid = fork();
	if (pid == -1) ERROR;
	if (pid == 0) {
		std::cerr << "Starting reverse forwarder." << std::endl;
		forward_sockets(app_sock, agent_sock);
		std::cerr << "Exiting reverse forwarder." << std::endl;
		exit(0);
	}
	close(agent_sock);
	close(app_sock);
}

void listen_agent(char *path) {
	auto len = strlen(path);
	if (len >= UNIX_PATH_MAX) {
		std::cerr << "Path is too long." << std::endl;
		exit(1);
	}
	sockaddr_un addr;
	addr.sun_family = AF_UNIX;
	strcpy(addr.sun_path, path);
	int sock = socket(AF_UNIX, SOCK_STREAM, 0);
	if (sock == -1) ERROR;
	unlink(path);
	if (bind(sock, (sockaddr *)&addr, offsetof(sockaddr_un, sun_path) + len + 1) == -1) ERROR;
	if (listen(sock, SOMAXCONN) == -1) ERROR;
	std::cerr << "Listening on " << path << std::endl;
	while (true) {
		int conn = accept(sock, nullptr, nullptr);
		if (conn == -1) ERROR;
		std::cerr << "Client connection established." << std::endl;
		int pid = fork();
		if (pid == -1) ERROR;
		if (pid == 0) {
			close(sock);
			process_connection(conn);
			exit(0);
		}
		close(conn);
	}
}

int main(int argc, char ** argv) {
	if (argc < 2) {
		std::cerr << "Please specify path of the agent socket." << std::endl;
		return 1;
	}
	listen_agent(argv[1]);
}
