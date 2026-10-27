<%@ include file="top.jspf" %>
<main class="auth"><p class="small"><a href="${ctx}/">Back to home</a></p><div class="card">
<h1>Log in</h1>
<form method="post" action="${ctx}/login">
<label for="email">Email</label><input id="email" name="email" type="email" value="<c:out value='${email}'/>" required>
<label for="pw">Password</label><input id="pw" name="password" type="password" required>
<button>Log in</button>
<c:if test="${not empty error}"><div class="msg err"><c:out value="${error}"/></div></c:if>
<c:if test="${not empty param.registered}"><div class="msg ok">Account created. Your account number is <c:out value="${param.registered}"/>. You can log in now.</div></c:if>
</form><p class="small">New here? <a href="${ctx}/register">Create an account</a></p></div></main></body></html>
