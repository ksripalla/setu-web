package moneytransfer.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import moneytransfer.AccountService;
import moneytransfer.AuthService;
import moneytransfer.BankException;
import moneytransfer.TransferResult;
import moneytransfer.TransferService;

@WebServlet("/app/transfer")
public class TransferServlet extends HttpServlet {
    private final AccountService accounts = new AccountService();
    private final TransferService transfers = new TransferService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        HttpSession s = req.getSession();
        req.setAttribute("flash", s.getAttribute("flash"));   // one-time message left by doPost
        s.removeAttribute("flash");
        show(req, res, UUID.randomUUID().toString());          // fresh request id for every new form
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        AuthService.User user = (AuthService.User) req.getSession().getAttribute("user");
        String requestId = req.getParameter("requestId");
        try {
            int from = Integer.parseInt(req.getParameter("from"));
            int to = Integer.parseInt(req.getParameter("to").trim());
            BigDecimal amount = new BigDecimal(req.getParameter("amount").trim());
            TransferResult r = transfers.transfer(user.userId(), from, to, amount, requestId);
            req.getSession().setAttribute("flash", r.replayed()
                ? "Already processed. No money moved again (transaction #" + r.transactionId() + ")."
                : "Transfer successful! Transaction #" + r.transactionId() + ".");
            res.sendRedirect(req.getContextPath() + "/app/transfer");   // Post/Redirect/Get: refresh cannot re-send
        } catch (NumberFormatException | NullPointerException e) {
            req.setAttribute("error", "Please enter valid account numbers and amount.");
            show(req, res, requestId);
        } catch (BankException e) {
            req.setAttribute("error", e.getMessage());
            show(req, res, requestId);                                   // same request id: a retry stays safe
        }
    }

    private void show(HttpServletRequest req, HttpServletResponse res, String requestId)
            throws ServletException, IOException {
        AuthService.User user = (AuthService.User) req.getSession().getAttribute("user");
        try {
            req.setAttribute("accounts", accounts.getAccounts(user.userId()));
        } catch (BankException e) {
            req.setAttribute("error", e.getMessage());
        }
        req.setAttribute("requestId", requestId);
        req.getRequestDispatcher("/WEB-INF/views/transfer.jsp").forward(req, res);
    }
}
