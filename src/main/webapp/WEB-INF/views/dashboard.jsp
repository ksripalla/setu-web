<%@ include file="top.jspf" %><%@ include file="nav.jspf" %>
<main>
<c:if test="${not empty error}"><div class="msg err"><c:out value="${error}"/></div></c:if>
<c:forEach var="a" items="${accounts}"><div class="card"><span class="small">Welcome back, <c:out value="${user.name}"/></span>
<div class="balance">Rs. <fmt:formatNumber value="${a.balance}" minFractionDigits="2" maxFractionDigits="2"/></div>
<div class="small">Account number <c:out value="${a.accountId}"/> (<c:out value="${a.status}"/>)
 &nbsp; <a href="${ctx}/app/transfer">Send money</a> &nbsp; <a href="${ctx}/app/statement?account=${a.accountId}">Full statement</a></div></div></c:forEach>
<div class="card"><h1>Recent activity</h1>
<c:choose><c:when test="${empty recent}"><p class="small">No transactions yet. <a href="${ctx}/app/transfer">Send your first transfer.</a></p></c:when>
<c:otherwise><table><thead><tr><th>Tx</th><th>Type</th><th>Other account</th><th class="num">Amount</th><th class="num">Balance after</th></tr></thead><tbody>
<c:forEach var="t" items="${recent}"><tr><td>#<c:out value="${t.transactionId}"/></td>
<td class="${t.type == 'DEBIT' ? 'debit' : 'credit'}">${t.type == 'DEBIT' ? 'Sent' : 'Received'}</td><td><c:out value="${t.counterpartyAccountId}"/></td>
<td class="num"><fmt:formatNumber value="${t.amount}" minFractionDigits="2" maxFractionDigits="2"/></td>
<td class="num"><fmt:formatNumber value="${t.balanceAfter}" minFractionDigits="2" maxFractionDigits="2"/></td></tr></c:forEach>
</tbody></table></c:otherwise></c:choose></div></main></body></html>
