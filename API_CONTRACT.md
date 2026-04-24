# ChipIn API Contract

This document outlines the core APIs available for the ChipIn UI integration. All `/api/**` endpoints require a Bearer token (JWT) to be passed in the `Authorization` header.

Swagger UI is available at: `http://localhost:8080/swagger-ui/index.html` (once the app is running).

---

## 1. Authentication (`/auth`)

### 1.1 Signup
*   **Endpoint**: `POST /auth/signup`
*   **Description**: Register a new user.
*   **Input Body** (all fields mandatory except phone):
    ```json
    {
      "name*": "John Doe",
      "email*": "john@example.com",
      "password*": "securepassword",
      "phone": "1234567890"
    }
    ```
*   **Output**: `200 OK` (User object)

### 1.2 Login
*   **Endpoint**: `POST /auth/login`
*   **Description**: Authenticate and receive a JWT token.
*   **Input Body** (all fields mandatory):
    ```json
    {
      "email*": "john@example.com",
      "password*": "securepassword"
    }
    ```
*   **Output**: `200 OK`
    ```json
    {
      "token": "eyJhbGci...",
      "expiresIn": 86400000
    }
    ```

### 1.3 Logout
*   **Endpoint**: `POST /auth/logout`
*   **Description**: Invalidate the user's token.
*   **Input Body** (email mandatory):
    ```json
    {
      "email*": "john@example.com"
    }
    ```
*   **Output**: `200 OK` ("Logout Successful, Token invalidated!")

---

## 2. User Profile (`/api/users`)

### 2.1 Get Current User
*   **Endpoint**: `GET /api/users/me`
*   **Description**: Returns the profile details of the currently logged-in user.
*   **Output**: `200 OK` (User entity)

### 2.2 Update Profile
*   **Endpoint**: `PUT /api/users/me`
*   **Description**: Update current user's name, phone, or profile picture. All fields optional.
*   **Input Body**:
    ```json
    {
      "name": "John Updated",
      "phone": "0987654321",
      "profilePicUrl": "https://link.to/pic.png"
    }
    ```
*   **Output**: `200 OK` (Updated User entity)

### 2.3 Get Default Currency
*   **Endpoint**: `GET /api/users/me/default-currency`
*   **Description**: Fetches the default currency of the currently authenticated user.
*   **Output**: `200 OK` (Currency object)

### 2.4 Disable User
*   **Endpoint**: `POST /api/users/disable`
*   **Description**: Disables a user account (Admin only usually, currently open based on email).
*   **Query Params**: `email*` (String)
*   **Output**: `200 OK` ("User disabled successfully.")

### 2.5 Enable User
*   **Endpoint**: `POST /api/users/enable`
*   **Description**: Enables a disabled user account.
*   **Query Params**: `email*` (String)
*   **Output**: `200 OK` ("User enabled successfully.")

---

## 3. Currencies (`/api/currencies`)

### 3.1 Get Currencies
*   **Endpoint**: `GET /api/currencies`
*   **Description**: Fetch available currencies.
*   **Query Params**: `groupId` (optional UUID) - Pass to fetch custom currencies specific to that group alongside global ones.
*   **Output**: `200 OK` (List of Currency objects)

### 3.2 Get Currency by ID
*   **Endpoint**: `GET /api/currencies/{id}`
*   **Description**: Fetch a specific currency by ID.
*   **Path Params**: `id*` (UUID)
*   **Output**: `200 OK` (Currency object) or `404 Not Found`

### 3.3 Create Currency
*   **Endpoint**: `POST /api/currencies`
*   **Description**: Create a new global currency.
*   **Input Body** (Currency object with mandatory fields: code*, name*, symbol*)
*   **Output**: `201 Created` (Currency object)

### 3.4 Delete Currency
*   **Endpoint**: `DELETE /api/currencies/{id}`
*   **Description**: Delete a currency by ID.
*   **Path Params**: `id*` (UUID)
*   **Output**: `204 No Content`

---

## 4. Groups (`/api/groups`)

### 4.1 Create Group
*   **Endpoint**: `POST /api/groups`
*   **Description**: Create a new group. The default currency MUST be a valid global currency.
*   **Input Body** (mandatory fields marked with *):
    ```json
    {
      "name*": "Goa Trip",
      "description": "Fun times",
      "imageUrl": "https://link.to/image.png",
      "type*": "TRIP",
      "simplifyDebt": true,
      "defaultCurrencyId*": "<global-currency-uuid>"
    }
    ```
*   **Output**: `200 OK` (GroupResponse object)

### 4.2 Add Member
*   **Endpoint**: `POST /api/groups/{groupId}/members`
*   **Description**: Add a user to a group (Admin only).
*   **Path Params**: `groupId*` (UUID)
*   **Input Body** (email mandatory):
    ```json
    {
      "email*": "friend@example.com",
      "isAdmin": false
    }
    ```
*   **Output**: `200 OK` ("Member added successfully")

### 4.3 Add Custom Currency to Group
*   **Endpoint**: `POST /api/groups/{groupId}/currencies/{currencyId}`
*   **Description**: Map an existing currency to a group with a specific custom name and locked exchange rate.
*   **Path Params**: `groupId*` (UUID), `currencyId*` (UUID)
*   **Query Params**: `name*` (String), `exchangeRate*` (Decimal)
*   **Output**: `200 OK` (GroupCurrency object)

### 4.4 Get Group Dashboard
*   **Endpoint**: `GET /api/groups/{groupId}/dashboard`
*   **Description**: The main view for a single group. Shows overall balances, list of expenses, and calculated settlements to square up.
*   **Path Params**: `groupId*` (UUID)
*   **Output**: `200 OK` (`GroupDashboardResponse`)
    *   Includes `targetCurrencyId`, `userBalances`, `expenses`, and `settlements` arrays.

### 4.5 Get Groups by User
*   **Endpoint**: `GET /api/groups/user/{userId}`
*   **Description**: Fetch groups for a specific user, including balance owed and last expense date.
*   **Path Params**: `userId*` (UUID)
*   **Output**: `200 OK` (`GroupsTabResponse`)
    ```json
    {
      "groups": [
        {
          "group": {
            "groupId": "uuid",
            "name": "Goa Trip",
            "description": "Fun times",
            "imageUrl": "https://link.to/image.png",
            "type": "TRIP",
            "simplifyDebt": true,
            "defaultCurrency": {
              "currencyId": "uuid",
              "code": "INR",
              "name": "Indian Rupee",
              "symbol": "₹",
              "isActive": true
            },
            "createdBy": "uuid",
            "createdAt": "2023-01-01T00:00:00",
            "updatedAt": "2023-01-01T00:00:00"
          },
          "amountOwedByUser": 250.0,
          "lastExpenseDate": "2023-01-15T12:00:00"
        }
      ]
    }
    ```

---

## 5. Home View (`/api/home`)

### 5.1 Groups View
*   **Endpoint**: `GET /api/home/groups`
*   **Description**: Aggregates total owed/owe across all groups for the user.
*   **Query Params**: `displayCurrencyId` (optional UUID) - The global currency to use for aggregation. If not provided, uses user's default currency or INR fallback.
*   **Output**: `200 OK` (`HomeGroupsResponse`)
    *   Includes total amounts and a breakdown array of `GroupSummaryDto` objects.

### 5.2 Friends View
*   **Endpoint**: `GET /api/home/friends`
*   **Description**: Aggregates net balances strictly against individual friends across all shared groups.
*   **Query Params**: `displayCurrencyId` (optional UUID) - Same as above.
*   **Output**: `200 OK` (`HomeFriendsResponse`)
    *   Includes total amounts and a breakdown array of `FriendSummaryDto` objects.

---

## 6. Expenses (`/api/groups/{groupId}/expenses`)

### 6.1 Create Expense
*   **Endpoint**: `POST /api/groups/{groupId}/expenses`
*   **Description**: Record a new expense in a group.
*   **Path Params**: `groupId*` (UUID)
*   **Input Body** (mandatory fields marked with *):
    ```json
    {
      "description*": "Dinner",
      "amount*": 500.0,
      "currencyId*": "<group-currency-uuid>",
      "splitType*": "EQUAL",
      "type": "FOOD",
      "receiptImgUrl": "https://link.to/receipt.png",
      "payers*": [ {"userId": "uuid", "paidAmount": 500.0} ],
      "splits*": [ 
          {"userId": "uuid", "amountOwed": 250.0},
          {"userId": "uuid2", "amountOwed": 250.0}
      ]
    }
    ```
*   **Output**: `200 OK` (Confirmation string)

### 6.2 Get Expense Details
*   **Endpoint**: `GET /api/groups/{groupId}/expenses/{expenseId}`
*   **Description**: Fetch details of a specific expense.
*   **Path Params**: `groupId*` (UUID), `expenseId*` (UUID)
*   **Output**: `200 OK` (`ExpenseDetailsResponse`)

---

## 7. Settlements (`/api/settlements`)

### 7.1 Record Settlement (Payment)
*   **Endpoint**: `POST /api/settlements`
*   **Description**: Record a manual payment to settle debts. This is recorded natively as an expense of type `SETTLEMENT`.
*   **Input Body** (mandatory fields marked with *):
    ```json
    {
      "groupId*": "<uuid>",
      "payerId*": "<uuid-who-paid>",
      "payeeId*": "<uuid-who-received>",
      "amount*": 250.0,
      "currencyId*": "<group-currency-uuid>",
      "notes": "Paid back via Venmo"
    }
    ```
*   **Output**: `200 OK` (Confirmation string)
