<%@ include file="top.jspf" %><%@ include file="nav.jspf" %>
<main><div class="card"><h1>Statement, account <c:out value="${accountId}"/></h1>
<c:if test="${not empty error}"><div class="msg err"><c:out value="${error}"/></div></c:if>
<c:choose><c:when test="${empty lines}"><p class="small">No transactions on this page.</p></c:when>
<c:otherwise><table><thead><tr><th>Date</th><th>Tx</th><th>Type</th><th>Other account</th><th class="num">Amount</th><th class="num">Balance after</th></tr></thead><tbody>
<c:forEach var="t" items="${lines}"><tr><td><c:out value="${t.when}"/></td><td>#<c:out value="${t.transactionId}"/></td>
<td class="${t.type == 'DEBIT' ? 'debit' : 'credit'}">${t.type == 'DEBIT' ? 'Sent' : 'Received'}</td><td><c:out value="${t.counterpartyAccountId}"/></td>
<td class="num"><fmt:formatNumber value="${t.amount}" minFractionDigits="2" maxFractionDigits="2"/></td>
<td class="num"><fmt:formatNumber value="${t.balanceAfter}" minFractionDigits="2" maxFractionDigits="2"/></td></tr></c:forEach></tbody></table></c:otherwise></c:choose>
<p class="small"><c:if test="${page > 1}"><a href="?account=${accountId}&page=${page - 1}">Newer</a> &nbsp; </c:if>Page ${page}
<c:if test="${hasNext}"> &nbsp; <a href="?account=${accountId}&page=${page + 1}">Older</a></c:if></p></div></main></body></html>
