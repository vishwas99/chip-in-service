# Currency Validation Refactoring

## Summary
Created a global method `validateAndGetGroupCurrency()` in `CurrencyService` to handle currency validation for groups consistently across the application. This replaces duplicate validation logic that was previously scattered in `ExpenseService` and `SettlementService`.

## Problem Statement
- `GroupCurrency` is used for custom currencies specific to a group
- Groups can also use global currencies from the `Currency` table
- Previously, validation logic was inconsistent:
  - `ExpenseService` had inline logic to check both GroupCurrency and Currency tables
  - `SettlementService` only checked the GroupCurrency table
- This led to potential bugs where settlements might reject valid global currencies

## Solution

### New Method: `CurrencyService.validateAndGetGroupCurrency()`
**Location**: `/home/vishwas/Projects/chip-in-service/src/main/java/com/chipIn/ChipIn/services/CurrencyService.java`

**Purpose**: Validates and retrieves a GroupCurrency for a group, handling both:
1. Custom group currencies (from `group_currencies` table)
2. Global currencies (from `currencies` table)

**Logic Flow**:
1. First, attempts to fetch from `GroupCurrency` table by ID
   - If found, validates it belongs to the requested group
   - Returns the GroupCurrency
2. If not found, attempts to fetch from global `Currency` table by ID
3. Validates the global currency:
   - Must be active
   - If it has a group association, it must be for the current group
4. Finds or creates the corresponding `GroupCurrency` mapping
5. Returns the validated GroupCurrency

**Benefits**:
- ✅ Consistent validation across all services
- ✅ Handles both global and custom currencies transparently
- ✅ Automatically creates GroupCurrency mappings when needed
- ✅ Comprehensive error messages for invalid scenarios
- ✅ Single source of truth for currency validation logic

## Files Modified

### 1. `CurrencyService.java`
**Changes**:
- Added `GroupCurrencyRepository` dependency (via constructor injection)
- Added imports for `GroupCurrency` and `User` entities
- Added new method `validateAndGetGroupCurrency(UUID groupId, UUID currencyId, User currentUser)`
- Comprehensive javadoc explaining the method's behavior

### 2. `ExpenseService.java`
**Changes**:
- Added `CurrencyService` dependency (via constructor injection)
- Replaced inline currency validation logic (lines 44-61) with single call:
  ```java
  GroupCurrency groupCurrency = currencyService.validateAndGetGroupCurrency(groupId, request.getCurrencyId(), currentUser);
  ```

### 3. `SettlementService.java`
**Changes**:
- Added `CurrencyService` dependency (via constructor injection)
- Replaced basic `groupCurrencyRepository.findById()` call with:
  ```java
  GroupCurrency currency = currencyService.validateAndGetGroupCurrency(request.getGroupId(), request.getCurrencyId(), currentUser);
  ```
- Removed unused `GroupCurrencyRepository` dependency
- Removed unused `java.util.List` import

## Error Handling
The validation method throws appropriate exceptions:
- `RuntimeException("Group not found")` - if group doesn't exist
- `IllegalArgumentException("Currency not found")` - if currency doesn't exist in either table
- `IllegalArgumentException("Currency is not active")` - if currency is inactive
- `IllegalArgumentException("Currency does not belong to this group")` - if GroupCurrency belongs to different group
- `IllegalArgumentException("This custom currency does not belong to this group")` - if global currency is assigned to another group

## Testing Recommendations
1. Test creating expenses with global currencies (should auto-create GroupCurrency mapping)
2. Test creating settlements with custom group currencies
3. Test with inactive currencies (should fail)
4. Test with currencies belonging to other groups (should fail)
5. Test that auto-created GroupCurrency mappings have correct exchange rates and metadata

## Build Status
✅ Project compiles successfully with no compilation errors

