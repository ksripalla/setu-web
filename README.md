# Setu - Money Transfer (Java, Servlets, JSP, JDBC, MySQL, Tomcat 9)

## Run it
1. Run `sql/schema.sql` in MySQL (creates database `moneytransfer_db`).
2. In `src/main/java`, copy `db.properties.example` to `db.properties` and enter your MySQL user and password.
3. Right-click the project, Run As, Run on Server, Tomcat v9.0.
4. Open http://localhost:8080/setu-web/

## Structure
- `moneytransfer/` services: AuthService, TransferService, AccountService (business rules + JDBC)
- `moneytransfer/web/` servlets and AuthFilter (login check and CSRF token)
- `WEB-INF/views/` JSP pages (reachable only through servlets)
- `index.html`, `css/` public landing page and styles
- `ConcurrencyTest` run as a Java Application to check no money is lost with 40 simultaneous threads
