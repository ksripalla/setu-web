package moneytransfer.web;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import moneytransfer.AuthService;
import moneytransfer.BankException;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private final AuthService auth = new AuthService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        HttpSession s = req.getSession(false);
        if (s != null && s.getAttribute("user") != null) {
            res.sendRedirect(req.getContextPath() + "/app/dashboard");
            return;
        }
        req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            AuthService.User user = auth.login(req.getParameter("email"), req.getParameter("password"));
            HttpSession old = req.getSession(false);
            if (old != null) {
                old.invalidate();             // new session id after login (prevents session fixation)
            }
            HttpSession s = req.getSession(true);
            s.setMaxInactiveInterval(15 * 60);
            s.setAttribute("user", user);
            s.setAttribute("csrf", UUID.randomUUID().toString());
            res.sendRedirect(req.getContextPath() + "/app/dashboard");
        } catch (BankException e) {
            req.setAttribute("error", e.getMessage());
            req.setAttribute("email", req.getParameter("email"));
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, res);
        }
    }
}
