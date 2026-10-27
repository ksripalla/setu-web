package moneytransfer.web;

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import moneytransfer.AccountService;
import moneytransfer.AuthService;
import moneytransfer.BankException;

@WebServlet("/app/dashboard")
public class DashboardServlet extends HttpServlet {
    private final AccountService accounts = new AccountService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        AuthService.User user = (AuthService.User) req.getSession().getAttribute("user");
        try {
            List<AccountService.AccountSummary> list = accounts.getAccounts(user.userId());
            req.setAttribute("accounts", list);
            if (!list.isEmpty()) {
                req.setAttribute("recent", accounts.getStatement(user.userId(), list.get(0).accountId(), 1, 5));
            }
        } catch (BankException e) {
            req.setAttribute("error", e.getMessage());
        }
        req.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(req, res);
    }
}
