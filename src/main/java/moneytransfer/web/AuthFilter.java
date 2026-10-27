package moneytransfer.web;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.*;

/** Guards every /app/* page: must be logged in, and every POST must carry the session's CSRF token. */
@WebFilter("/app/*")
public class AuthFilter implements Filter {
    @Override
    public void doFilter(ServletRequest rq, ServletResponse rs, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) rq;
        HttpServletResponse res = (HttpServletResponse) rs;
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            res.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        if ("POST".equals(req.getMethod())) {
            String token = req.getParameter("csrf");
            if (token == null || !token.equals(session.getAttribute("csrf"))) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token. Go back and reload the page.");
                return;
            }
        }
        res.setHeader("Cache-Control", "no-store");   // Back button after logout must not show account pages
        chain.doFilter(rq, rs);
    }
}
