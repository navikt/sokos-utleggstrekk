# Code Review: sokos-utleggstrekk

**Date:** 2026-02-05  
**Reviewer:** GitHub Copilot  
**Project:** sokos-utleggstrekk - Application for handling deductions from Norwegian Tax Administration

## Executive Summary

This code review has analyzed a Kotlin/Ktor application that processes deductions (utleggstrekk) from the Norwegian Tax Administration (Skatteetaten) and forwards them to the OS (Oppdrag System) via IBM MQ. The codebase is generally well-structured and uses modern Kotlin practices, but there are several areas that need attention.

### Overall Assessment
- **Code Quality:** Good
- **Security:** Acceptable (no hardcoded credentials, proper use of environment variables)
- **Test Coverage:** 17 test files, 42 tests (1 failing)
- **Build Status:** ✅ Successful
- **Linting Status:** ✅ Passes ktlint

## Key Findings

### Critical Issues

1. **Failing Test (Priority: High)**
   - Location: `BehandleTrekkServiceNyTest`
   - Impact: There is 1 failing test out of 42 tests
   - Recommendation: Investigate and fix the failing test immediately

2. **Potential Null Pointer Exception (Priority: High)**
   - Location: `JmsListenerService.kt:50`
   - Issue: Force unwrapping with `!!` operator: `if (receipt.mmel!!.alvorlighetsgrad != "00")`
   - Impact: Application will crash if `mmel` is null
   - Recommendation: Add proper null-safety check

3. **Incomplete Error Handling (Priority: High)**
   - Location: `JmsProducerService.kt:25`
   - Issue: TODO comment indicates error handling is incomplete
   - Quote: `// TODO: Må legge inn feilhåndtering + manuell håndtering`
   - Impact: Failed MQ messages may not be handled properly

### High Priority Issues

4. **Broad Exception Catching (Priority: Medium-High)**
   - Locations:
     - `JmsListenerService.kt:43`: `catch (exception: Exception)`
     - `JmsProducerService.kt:31`: `catch (exception: Exception)`
     - `CommonConfig.kt:62`: `catch (e: Exception)`
   - Issue: Catching generic `Exception` can hide specific errors
   - Recommendation: Catch specific exception types and handle them appropriately

5. **Missing Slack Notification Implementation (Priority: Medium)**
   - Location: `JmsListenerService.kt:62`
   - Issue: TODO comment for Slack notification on errors
   - Quote: `// TODO sjekke/vurdere om det skal sendes melding til slack og evt utføre det.`
   - Impact: Errors may not be properly alerted

6. **Inefficient Data Fetching (Priority: Medium)**
   - Location: `UtleggsTrekkService.kt:41-42`
   - Issue: Fetches all deductions every time instead of just new ones
   - Quote: `// TODO Denne henter alle hver gang, Den bør bare hente nye når den skal brukes mot skatt regelmessig`
   - Impact: Performance and unnecessary network traffic

7. **Non-Persisted Correlation ID (Priority: Medium)**
   - Location: `SkeClient.kt:50`
   - Issue: Correlation ID is generated but not stored
   - Quote: `append("Korrelasjonsid", UUID.randomUUID().toString()) // TODO: Hvis dette skal være noe poeng må den tas vare på et sted!`
   - Impact: Cannot track requests across systems

### Medium Priority Issues

8. **Unused/Deprecated Method (Priority: Medium)**
   - Location: `SkeClient.kt:31`
   - Issue: Method marked as should not be used
   - Quote: `// TODO: skal ikke brukes`
   - Recommendation: Remove or mark as deprecated if no longer needed

9. **Code Organization Issues**
   - Location: `CommonConfig.kt:23-24, 27, 40`
   - Issues:
     - File needs renaming (not truly "common")
     - Multiple global loggers instead of one
     - ExperimentalSerializationApi annotation may not be needed
   - Recommendation: Refactor for better organization

10. **Naming and Structure TODOs (Priority: Low-Medium)**
    - `BehandleTrekkService.kt:20-21`: Return type and domain object naming
    - `BehandleTrekkService.kt:50`: Function needs better name
    - `DatabaseService.kt:22`: Service may not be needed
    - `Repository.kt:130`: Named parameters in batch operations

11. **DateTime Handling Inconsistency (Priority: Low)**
    - Locations:
      - `UtleggstrekkTable.kt:60-62`
      - `TrekkPeriodeTable.kt:23`
    - Issue: Mix of Java LocalDateTime and Kotlinx datetime
    - Recommendation: Standardize on one approach

### Code Quality Observations

12. **Good Practices Identified:**
    - ✅ Proper use of environment variables for configuration
    - ✅ No hardcoded credentials
    - ✅ Vault integration for production secrets
    - ✅ Proper use of Mutex for token caching in `MaskinportenAccessTokenClient`
    - ✅ Transaction handling in database operations
    - ✅ Proper MQ commit/rollback logic
    - ✅ JWT validation with proper audience checks
    - ✅ Structured logging with correlation IDs

13. **Minor Issues:**
    - Empty catch block returning `emptyList()` in `SkeClient.kt:58` - consider logging at higher level
    - Hard-coded "Klientid" value `"NAV/0.1"` - consider making configurable
    - Migration requires admin user role hardcoded in SQL

## Detailed Findings by Category

### Security
- **Status:** ✅ No critical security issues found
- Credentials properly managed through environment variables
- Vault integration for production credentials
- JWT authentication properly implemented
- Token caching with proper expiration handling

### Error Handling
- **Status:** ⚠️ Needs improvement
- Several TODOs indicating incomplete error handling
- Broad exception catching in multiple places
- Missing Slack notifications for critical errors

### Performance
- **Status:** ⚠️ Needs optimization
- Fetching all deductions instead of just new ones
- Consider implementing pagination or incremental fetches

### Testing
- **Status:** ⚠️ One failing test
- 42 tests total, 41 passing
- Failing test needs immediate attention

### Code Maintainability
- **Status:** 👍 Good
- Code is well-structured and follows Kotlin conventions
- Passes ktlint checks
- Good separation of concerns (api, service, client, database layers)

## Recommendations

### Immediate Actions (Must Fix)
1. Fix the failing test in `BehandleTrekkServiceNyTest`
2. Add null safety check for `receipt.mmel` in `JmsListenerService.kt:50`
3. Implement complete error handling in `JmsProducerService`

### Short-term Actions (Should Fix)
4. Replace broad exception catching with specific exception types
5. Implement Slack notification for errors
6. Optimize data fetching to retrieve only new deductions
7. Persist and track correlation IDs for request tracing

### Long-term Improvements (Nice to Have)
8. Refactor `CommonConfig.kt` and address naming issues
9. Standardize on one datetime library (Java or Kotlinx)
10. Clean up TODOs and deprecated code
11. Add more comprehensive integration tests

## Testing Status

### Test Results
```
42 tests completed, 1 failed
- BehandleTrekkServiceTest: 9/9 passed
- LifecycleTest: 4/4 passed  
- SlackServiceTest: 4/4 passed
- TSSIdTest: 3/3 passed
- UtleggsTrekkServiceTest: 2/2 passed
- BehandleTrekkServiceNyTest: FAILED
```

### Build Status
```
BUILD SUCCESSFUL in 58s (excluding tests)
24 actionable tasks: 17 executed, 7 up-to-date
```

## Security Summary

No security vulnerabilities were identified during this review. The application properly:
- Uses environment variables for sensitive configuration
- Integrates with Vault for production credentials
- Implements JWT authentication
- Manages access tokens with proper expiration

## Conclusion

The sokos-utleggstrekk application is a well-structured Kotlin application with good coding practices. The main areas needing attention are:
1. Fix the failing test
2. Improve error handling and null safety
3. Implement pending features (Slack notifications, better data fetching)
4. Address the numerous TODO comments throughout the codebase

The codebase shows good understanding of Kotlin idioms, proper separation of concerns, and appropriate use of modern frameworks. With the recommended fixes, especially around error handling and null safety, this will be a robust production application.

---

**Total TODOs Found:** 22  
**Critical Issues:** 3  
**High Priority Issues:** 4  
**Medium Priority Issues:** 6  
**Low Priority Issues:** 9
