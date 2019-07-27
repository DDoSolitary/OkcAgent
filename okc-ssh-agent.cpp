#include <iostream>
#include <sstream>
#include <cassert>
#include <cstdlib>
#include <unistd.h>
#include <signal.h>
#include <uv.h>

#ifdef DEBUG
#define PRINT_ERR(s) std::cerr << "Error on line " << __LINE__ << ": " << s << std::endl
#else
#define PRINT_ERR(s) std::cerr << "Error: " << s << std::endl
#endif

#define UV_CHECKED(expr, exit_expr) { \
	auto r = expr; \
	if (r < 0) { \
		PRINT_ERR(uv_strerror(r)); \
		exit_expr; \
	} \
}
#define UV_CHECKED_CTX(expr, ctx) UV_CHECKED(expr, { \
	delete (ctx); \
	return; \
})
#define UV_CHECKED_EXIT(expr) UV_CHECKED(expr, exit(1))

struct stream_context {
	uv_stream_t *stream;
	bool half_close;
	stream_context() : stream(nullptr), half_close(false) {}
};

struct context {
	uv_tcp_t *app_server;
	uv_timer_t *timer;
	stream_context app_conn, agent_conn;
	context() : app_server(nullptr), timer(nullptr), app_conn(), agent_conn() {}
	bool should_delete() {
		return !app_conn.stream && !agent_conn.stream;
	}
	~context();
};

struct write_context {
	context *ctx;
	uv_buf_t buffer;
};

auto loop = uv_default_loop();

template<typename T>
T *allocate() {
	/* Deleting a pointer without knowing its original type produces
	 * undefined behavior in C++, so we have to use C-style memory
	 * management for subtypes of uv_handle_t. */
	return (T *)malloc(sizeof(T));
}

template<typename T>
void uv_close_checked(T *handle) {
	if (!uv_is_closing((uv_handle_t *)handle)) {
		uv_close((uv_handle_t *)handle, [](uv_handle_t *h) { free(h); });
	}
}

context::~context() {
	if (this->app_server) uv_close_checked(this->app_server);
	if (this->timer) uv_close_checked(this->timer);
	if (this->app_conn.stream) uv_close_checked(this->app_conn.stream);
	if (this->agent_conn.stream) uv_close_checked(this->agent_conn.stream);
}

void on_allocate_buffer(uv_handle_t *handle, size_t suggested_size, uv_buf_t *buf) {
	*buf = uv_buf_init(new char[suggested_size], suggested_size);
}

void on_shutdown(uv_shutdown_t *req, int status) {
	auto stream_ctx = (stream_context *)req->data;
	auto ctx = (context *)stream_ctx->stream->data;
	delete req;
	UV_CHECKED_CTX(status, ctx);
	if (stream_ctx->half_close) {
		uv_close_checked(stream_ctx->stream);
		stream_ctx->stream = nullptr;
		if (ctx->should_delete()) delete ctx;
	} else stream_ctx->half_close = true;
}

void on_write(uv_write_t *req, int status) {
	auto wctx = (write_context *)req->data;
	auto ctx = wctx->ctx;
	delete wctx->buffer.base;
	delete wctx;
	delete req;
	UV_CHECKED_CTX(status, ctx);
}

void on_read(uv_stream_t *stream, ssize_t nread, const uv_buf_t *buf) {
	auto ctx = (context *)stream->data;
	stream_context *src, *dest;
	if (stream == ctx->agent_conn.stream) {
		src = &ctx->agent_conn;
		dest = &ctx->app_conn;
	} else {
		src = &ctx->app_conn;
		dest = &ctx->agent_conn;
	}
	if (nread <= 0) {
		if (buf->base) delete buf->base;
		if (nread == 0) return;
		if (nread == UV_EOF) {
			auto req = new uv_shutdown_t;
			req->data = dest;
			UV_CHECKED(uv_shutdown(req, dest->stream, &on_shutdown), {
				delete req;
				delete ctx;
			});
			if (src->half_close) {
				uv_close_checked(src->stream);
				src->stream = nullptr;
				if (ctx->should_delete()) delete ctx;
			} else src->half_close = true;
			return;
		}
		UV_CHECKED_CTX(nread, ctx);
	}
	auto req = new uv_write_t;
	auto write_ctx = new write_context { ctx, uv_buf_init(buf->base, nread) };
	req->data = write_ctx;
	UV_CHECKED(uv_write(req, dest->stream, &write_ctx->buffer, 1, &on_write), {
		delete buf->base;
		delete write_ctx;
		delete req;
		delete ctx;
	});
}

void on_app_connected(uv_stream_t *stream, int status) {
	auto ctx = (context *)stream->data;
	if (ctx->timer) {
		uv_close_checked(ctx->timer);
		ctx->timer = nullptr;
	}
	UV_CHECKED_CTX(status, ctx);
	ctx->app_conn.stream = (uv_stream_t *)allocate<uv_tcp_t>();
	UV_CHECKED_EXIT(uv_tcp_init(loop, (uv_tcp_t *)ctx->app_conn.stream));
	ctx->app_conn.stream->data = ctx;
	UV_CHECKED_CTX(uv_accept((uv_stream_t *)ctx->app_server, ctx->app_conn.stream), ctx);
	uv_close_checked(ctx->app_server);
	ctx->app_server = nullptr;
	UV_CHECKED_CTX(uv_read_start(ctx->agent_conn.stream, &on_allocate_buffer, &on_read), ctx);
	UV_CHECKED_CTX(uv_read_start(ctx->app_conn.stream, &on_allocate_buffer, &on_read), ctx);
}

void on_timeout(uv_timer_t *timer) {
	PRINT_ERR("Timed out waiting for app to connect.");
	delete (context *)timer->data;	
}

void on_client_connected(uv_stream_t *agent_server, int status) {
	auto ctx = new context;
	UV_CHECKED_CTX(status, ctx);
	ctx->agent_conn.stream = (uv_stream_t *)allocate<uv_pipe_t>();
	UV_CHECKED_EXIT(uv_pipe_init(loop, (uv_pipe_t *)ctx->agent_conn.stream, false));
	ctx->agent_conn.stream->data = ctx;
	UV_CHECKED_CTX(uv_accept(agent_server, ctx->agent_conn.stream), ctx);
	sockaddr_in addr;
	uv_ip4_addr("127.0.0.1", 0, &addr);
	ctx->app_server = allocate<uv_tcp_t>();
	UV_CHECKED_EXIT(uv_tcp_init(loop, ctx->app_server));
	ctx->app_server->data = ctx;
	UV_CHECKED_CTX(uv_tcp_bind(ctx->app_server, (const sockaddr *)&addr, 0), ctx);
	UV_CHECKED_CTX(uv_listen((uv_stream_t *)ctx->app_server, 1, &on_app_connected), ctx);
	int addr_len = sizeof(addr);
	UV_CHECKED_CTX(uv_tcp_getsockname(ctx->app_server, (sockaddr *)&addr, &addr_len), ctx);
	std::stringstream cmd_ss;
	cmd_ss << "am broadcast"
		<< " -n org.ddosolitary.okcagent/.ProxyReceiver"
		<< " -a org.ddosolitary.okcagent.action.RUN_SSH_AGENT"
		<< " --ei org.ddosolitary.okcagent.extra.PROXY_PORT " << ntohs(addr.sin_port);
	system(cmd_ss.str().c_str());
	ctx->timer = allocate<uv_timer_t>();
	UV_CHECKED_EXIT(uv_timer_init(loop, ctx->timer));
	ctx->timer->data = ctx;
	UV_CHECKED_CTX(uv_timer_start(ctx->timer, &on_timeout, 10000, 0), ctx);
}

int main(int argc, char ** argv) {
	signal(SIGPIPE, SIG_IGN);
	if (argc < 2) {
		PRINT_ERR("Please specify path of the agent socket.");
		return 1;
	}
	unlink(argv[1]);
	uv_pipe_t agent_server;
	UV_CHECKED_EXIT(uv_pipe_init(loop, &agent_server, false));
	UV_CHECKED_EXIT(uv_pipe_bind(&agent_server, argv[1]));
	UV_CHECKED_EXIT(uv_listen((uv_stream_t *)&agent_server, SOMAXCONN, &on_client_connected));
	return uv_run(loop, UV_RUN_DEFAULT);
}
