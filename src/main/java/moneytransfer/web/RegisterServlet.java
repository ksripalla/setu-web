package moneytransfer.web;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import moneytransfer.AuthService;
import moneytransfer.BankException;

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {
    private final AuthService auth = new AuthService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            int acct = auth.register(req.getParameter("name"), req.getParameter("email"), req.getParameter("password"));
            res.sendRedirect(req.getContextPath() + "/login?registered=" + acct);   // Post/Redirect/Get
        } catch (BankException e) {
            req.setAttribute("error", e.getMessage());
            req.setAttribute("name", req.getParameter("name"));
            req.setAttribute("email", req.getParameter("email"));
            req.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(req, res);
        }
    }
}
