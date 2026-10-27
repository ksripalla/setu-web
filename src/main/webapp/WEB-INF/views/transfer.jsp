<%@ include file="top.jspf" %><%@ include file="nav.jspf" %>
<main><div class="card"><h1>Send money</h1>
<form method="post" action="${ctx}/app/transfer">
<input type="hidden" name="csrf" value="${csrf}"><input type="hidden" name="requestId" value="${requestId}">
<label for="from">From account</label>
<select id="from" name="from"><c:forEach var="a" items="${accounts}"><option value="${a.accountId}">Account ${a.accountId} (Rs. <fmt:formatNumber value="${a.balance}" minFractionDigits="2"/>)</option></c:forEach></select>
<label for="to">Receiver account number</label><input id="to" name="to" inputmode="numeric" required>
<label for="amt">Amount (Rs.)</label><input id="amt" name="amount" inputmode="decimal" placeholder="0.00" required>
<button>Send money</button>
<c:if test="${not empty error}"><div class="msg err"><c:out value="${error}"/></div></c:if>
<c:if test="${not empty flash}"><div class="msg ok"><c:out value="${flash}"/></div></c:if>
</form>
<p class="small">Each form has its own request ID, so a double-click or a refresh can never move the money twice.</p></div></main></body></html>
