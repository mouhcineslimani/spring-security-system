# Spring Security Configuration with Spring Boot, DataSource, and JPA

This README provides documentation for configuring Spring Security in a Spring Boot project, with a focus on DataSource and JPA configuration for data persistence.

## DataSource Configuration

The DataSource is configured to connect the application to a MySQL database. Below are the details of the configuration:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/security
    username: root
    password:
    driver-class-name: com.mysql.jdbc.Driver
  jpa:
    hibernate:
      show-sql: true
      ddl-auto: create-drop
      properties:
        hibernate:
          format_sql: true
      database: mysql
      database-platform: org.hibernate.dialect.mysqlDialect
```

- url: The URL for connecting to the MySQL database.
- username: The username used to connect to the database.
- password: The password associated with the user.
- driver-class-name: The JDBC MySQL driver class used for the connection.

- show-sql: Enables the display of SQL queries generated by Hibernate in the console.
- ddl-auto: Sets the mode for automatic schema generation (here, "create-drop" recreates the - database on each application startup).
- format_sql: Enables the formatting of SQL queries for better readability.
- database: Specifies the type of database used (here, MySQL).
- database-platform: Defines the Hibernate dialect specific to MySQL.


les étapes : 
1.  User implements UserDetails 
2.  JwtService : 
```
  package org.mql.security.config;

  import java.security.Key;
  import java.util.Date;
  import java.util.HashMap;
  import java.util.Map;
  import java.util.function.Function;

  import org.springframework.security.core.userdetails.UserDetails;
  import org.springframework.stereotype.Service;

  import io.jsonwebtoken.Claims;
  import io.jsonwebtoken.Jwts;
  import io.jsonwebtoken.SignatureAlgorithm;
  import io.jsonwebtoken.io.Decoders;
  import io.jsonwebtoken.security.Keys;

  @Service
  public class JwtService {

      // Hardcoded secret key (not recommended for production, consider using a dynamic and secure key)
      private static final String SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

      /**
      * Extracts the username from the JWT token.
      *
      * @param token The JWT token.
      * @return The extracted username.
      */
      public String extractUserName(String token) {
          return extractClaim(token, Claims::getSubject);
      }

      /**
      * Extracts a specific claim from the JWT token.
      *
      * @param token           The JWT token.
      * @param claimsResolver  A function to resolve the desired claim.
      * @param <T>             The type of the claim.
      * @return The resolved claim.
      */
      public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
          final Claims claims = extractAllClaims(token);
          return claimsResolver.apply(claims);
      }

      /**
      * Generates a JWT token for a user with additional claims.
      *
      * @param extraClaims     Additional claims to be included in the token.
      * @param userDetails     User details for whom the token is generated.
      * @return The generated JWT token.
      */
      public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
          return Jwts.builder()
                  .setClaims(extraClaims)
                  .setSubject(userDetails.getUsername())
                  .setIssuedAt(new Date(System.currentTimeMillis()))
                  .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 24))
                  .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                  .compact();
      }

      /**
      * Generates a JWT token for a user.
      *
      * @param userDetails User details for whom the token is generated.
      * @return The generated JWT token.
      */
      public String generateToken(UserDetails userDetails) {
          return generateToken(new HashMap<>(), userDetails);
      }

      /**
      * Extracts all claims from the JWT token.
      *
      * @param token The JWT token.
      * @return All claims in the token.
      */
      private Claims extractAllClaims(String token) {
          return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
      }

      /**
      * Checks if the JWT token is valid for a given user.
      *
      * @param token         The JWT token.
      * @param userDetails   User details to check against the token.
      * @return True if the token is valid for the user, false otherwise.
      */
      public boolean isTokenValid(String token, UserDetails userDetails) {
          final String userName = extractUserName(token);
          return userName.equals(userDetails.getUsername()) && !isTokenNotExpired(token);
      }

      /**
      * Checks if the JWT token has not expired.
      *
      * @param token The JWT token.
      * @return True if the token has not expired, false otherwise.
      */
      private boolean isTokenNotExpired(String token) {
          return extractExpirationTime(token).before(new Date());
      }

      /**
      * Extracts the expiration time from the JWT token.
      *
      * @param token The JWT token.
      * @return The expiration time.
      */
      private Date extractExpirationTime(String token) {
          return extractClaim(token, Claims::getExpiration);
      }

      /**
      * Gets the signing key for JWT token verification.
      *
      * @return The signing key.
      */
      private Key getSigningKey() {
          byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
          return Keys.hmacShaKeyFor(keyBytes);
      }
  }
```

3. JwtAuthFilter : 
```
  package org.mql.security.config;

  import java.io.IOException;

  import org.springframework.lang.NonNull;
  import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
  import org.springframework.security.core.context.SecurityContextHolder;
  import org.springframework.security.core.userdetails.UserDetails;
  import org.springframework.security.core.userdetails.UserDetailsService;
  import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
  import org.springframework.stereotype.Component;
  import org.springframework.web.filter.OncePerRequestFilter;

  import jakarta.servlet.FilterChain;
  import jakarta.servlet.ServletException;
  import jakarta.servlet.http.HttpServletRequest;
  import jakarta.servlet.http.HttpServletResponse;
  import lombok.RequiredArgsConstructor;

  @Component
  @RequiredArgsConstructor
  public class JwtAuthFilter extends OncePerRequestFilter {

      private final JwtService jwtService;
      private final UserDetailsService userDetailService;

      @Override
      protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
              @NonNull FilterChain filterChain) throws IOException, ServletException {
          // Extract the Authorization header from the request
          final String authHeader = request.getHeader("Authorization");
          final String jwt;
          final String userEmail;

          // Check if Authorization header is present and starts with "Bearer "
          if (authHeader == null || !authHeader.startsWith("Bearer ")) {
              // If not, continue with the next filter
              filterChain.doFilter(request, response);
              return;
          }

          // Extract the JWT token from the Authorization header
          jwt = authHeader.substring(7);

          // Extract userEmail from the JWT token
          userEmail = jwtService.extractUserName(jwt);

          // If userEmail is not null and user is not already authenticated
          if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
              // Load user details from the UserDetailsService
              UserDetails userDetails = this.userDetailService.loadUserByUsername(userEmail);

              // Check if the token is valid for the user
              if (jwtService.isTokenValid(jwt, userDetails)) {
                  // If valid, create an authentication token and set it in the SecurityContext
                  UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                          userDetails, null, userDetails.getAuthorities());
                  authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                  SecurityContextHolder.getContext().setAuthentication(authenticationToken);
              }
          }

          // Continue with the next filter
          filterChain.doFilter(request, response);
      }
  }

```

4. ApplicationConfig
```
  package org.mql.security.config;

  import org.mql.security.repositories.UserRepository;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.security.authentication.AuthenticationManager;
  import org.springframework.security.authentication.AuthenticationProvider;
  import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
  import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
  import org.springframework.security.core.userdetails.UserDetailsService;
  import org.springframework.security.core.userdetails.UsernameNotFoundException;
  import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
  import org.springframework.security.crypto.password.PasswordEncoder;

  import lombok.RequiredArgsConstructor;

  @Configuration
  @RequiredArgsConstructor
  public class ApplicationConfig {

      // Injected repository for accessing user data
      private final UserRepository repository;

      /**
      * Configures a bean for the UserDetailsService interface.
      * It retrieves user details from the repository based on the username.
      *
      * @return An implementation of UserDetailsService.
      */
      @Bean
      public UserDetailsService userDetailsService() {
          return username -> repository.findByEmail(username)
                  .orElseThrow(() -> new UsernameNotFoundException("User not found"));
      }

      /**
      * Configures a bean for the AuthenticationProvider interface.
      * It uses a DaoAuthenticationProvider with the custom UserDetailsService
      * and a PasswordEncoder for authentication.
      *
      * @return An implementation of AuthenticationProvider.
      */
      @Bean
      public AuthenticationProvider authenticationProvider() {
          DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
          authProvider.setUserDetailsService(userDetailsService());
          authProvider.setPasswordEncoder(passwordEncoder());
          return authProvider;
      }

      /**
      * Configures a bean for the PasswordEncoder interface.
      * It provides an instance of BCryptPasswordEncoder, a widely-used password hashing algorithm.
      *
      * @return An implementation of PasswordEncoder.
      */
      @Bean
      public PasswordEncoder passwordEncoder() {
          return new BCryptPasswordEncoder();
      }

      /**
      * Configures a bean for the AuthenticationManager interface.
      * It retrieves the AuthenticationManager from the provided AuthenticationConfiguration.
      *
      * @param config AuthenticationConfiguration used to retrieve the AuthenticationManager.
      * @return An implementation of AuthenticationManager.
      * @throws Exception if an exception occurs while retrieving the AuthenticationManager.
      */
      @Bean
      public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
          return config.getAuthenticationManager();
      }

  }

```

5. SecurityConfig
```
  package org.mql.security.config;

  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.security.authentication.AuthenticationProvider;
  import org.springframework.security.config.annotation.web.builders.HttpSecurity;
  import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
  import org.springframework.security.config.http.SessionCreationPolicy;
  import org.springframework.security.web.SecurityFilterChain;
  import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

  import lombok.RequiredArgsConstructor;

  @Configuration
  @EnableWebSecurity
  @RequiredArgsConstructor
  public class SecurityConfig {

      // Injected JwtAuthFilter for handling JWT authentication
      private final JwtAuthFilter jwtAuthFilter;

      // Injected AuthenticationProvider for custom authentication
      private final AuthenticationProvider authenticationProvider;

      /**
      * Configures the security filters and policies for HTTP requests.
      *
      * @param http HttpSecurity object to configure security settings.
      * @return A SecurityFilterChain configured with specified security settings.
      * @throws Exception if an exception occurs during configuration.
      */
      @Bean
      public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
          http
              .csrf().disable() // Disable CSRF protection
              .authorizeHttpRequests()
                  .requestMatchers("/api/v1/auth/**").permitAll() // Allow unauthenticated access to certain paths
                  .anyRequest().authenticated() // Require authentication for any other requests
              .and()
              .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Disable session creation (stateless)
              .and()
              .authenticationProvider(authenticationProvider) // Set custom AuthenticationProvider
              .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class); // Add JwtAuthFilter before the default UsernamePasswordAuthenticationFilter

          return http.build();
      }
  }

```