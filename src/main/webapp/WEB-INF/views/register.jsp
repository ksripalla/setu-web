<%@ include file="top.jspf" %>
<main class="auth"><p class="small"><a href="${ctx}/">Back to home</a></p><div class="card">
<h1>Create your account</h1>
<form method="post" action="${ctx}/register">
<label for="name">Full name</label><input id="name" name="name" value="<c:out value='${name}'/>" required>
<label for="email">Email</label><input id="email" name="email" type="email" value="<c:out value='${email}'/>" required>
<label for="pw">Password</label><input id="pw" name="password" type="password" minlength="8" required>
<p class="small">At least 8 characters. New accounts start with a demo balance of Rs. 1,000.00.</p>
<button>Create account</button>
<c:if test="${not empty error}"><div class="msg err"><c:out value="${error}"/></div></c:if>
</form><p class="small">Already registered? <a href="${ctx}/login">Log in</a></p></div></main></body></html>
