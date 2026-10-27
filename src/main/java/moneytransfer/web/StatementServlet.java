package moneytransfer.web;

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import moneytransfer.AccountService;
import moneytransfer.AuthService;
import moneytransfer.BankException;

@WebServlet("/app/statement")
public class StatementServlet extends HttpServlet {
    static final int PAGE_SIZE = 10;
    private final AccountService accounts = new AccountService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        AuthService.User user = (AuthService.User) req.getSession().getAttribute("user");
        try {
            List<AccountService.AccountSummary> list = accounts.getAccounts(user.userId());
            int accountId = list.isEmpty() ? 0 : list.get(0).accountId();
            int page = 1;
            try {
                accountId = Integer.parseInt(req.getParameter("account"));
                page = Math.max(1, Integer.parseInt(req.getParameter("page")));
            } catch (NumberFormatException e) {
                // keep defaults
            }
            List<AccountService.StatementLine> lines = accounts.getStatement(user.userId(), accountId, page, PAGE_SIZE);
            req.setAttribute("accountId", accountId);
            req.setAttribute("page", page);
            req.setAttribute("lines", lines);
            req.setAttribute("hasNext", lines.size() == PAGE_SIZE);
        } catch (BankException e) {
            req.setAttribute("error", e.getMessage());
        }
        req.getRequestDispatcher("/WEB-INF/views/statement.jsp").forward(req, res);
    }
}
