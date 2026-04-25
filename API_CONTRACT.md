# ChipIn API Contract

This document outlines the core APIs available for the ChipIn UI integration. All `/api/**` endpoints require a Bearer token (JWT) to be passed in the `Authorization` header.

Swagger UI is available at: `http://localhost:8080/swagger-ui/index.html` (once the app is running).

---

## 1. Authentication (`/auth`)

### 1.1 Signup
*   **Endpoint**: `POST /auth/signup`
*   **Description**: Register a new user. If the user's email already exists with a `PENDING_INVITE` status, this will update their account with the new details and activate it.
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

### 2.6 Search Users
*   **Endpoint**: `GET /api/users/search`
*   **Description**: Searches for users by name or email (case-insensitive). Useful for populating a dropdown or autocomplete field when adding users to a group.
*   **Query Params**: `query*` (String)
*   **Output**: `200 OK` (List of User objects)

### 2.7 Get Friends
*   **Endpoint**: `GET /api/users/friends`
*   **Description**: Gets a list of all users the authenticated user shares a group with.
*   **Output**: `200 OK` (List of `FriendResponse` objects)
    ```json
    [
      {
        "userId": "uuid",
        "name": "Friend Name",
        "email": "friend@example.com",
        "profilePicUrl": "https://link.to/pic.png"
      }
    ]
    ```

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

### 4.1 Get My Groups (For Dropdowns)
*   **Endpoint**: `GET /api/groups/me`
*   **Description**: Get all groups the currently logged-in user is a part of. Useful for dropdowns.
*   **Output**: `200 OK` (List of `GroupResponse` objects)

### 4.2 Create Group
*   **Endpoint**: `POST /api/groups`
*   **Description**: Create a new group. The default currency MUST be a valid global currency. The authenticated user who creates the group is automatically added as an admin member.
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

### 4.3 Add Existing Member to Group
*   **Endpoint**: `POST /api/groups/{groupId}/members`
*   **Description**: Add an *already registered* user to a group. Only group admins can perform this action.
*   **Path Params**: `groupId*` (UUID)
*   **Input Body** (email mandatory):
    ```json
    {
      "email*": "friend@example.com",
      "isAdmin": false
    }
    ```
*   **Output**: `200 OK` ("Member added successfully")

### 4.4 Add Custom Currency to Group
*   **Endpoint**: `POST /api/groups/{groupId}/currencies/{currencyId}`
*   **Description**: Map an existing currency to a group with a specific custom name and locked exchange rate.
*   **Path Params**: `groupId*` (UUID), `currencyId*` (UUID)
*   **Query Params**: `name*` (String), `exchangeRate*` (Decimal)
*   **Output**: `200 OK` (GroupCurrency object)

### 4.5 Get Group Dashboard
*   **Endpoint**: `GET /api/groups/{groupId}/dashboard`
*   **Description**: The main view for a single group. Shows overall balances, list of expenses, and calculated settlements to square up.
*   **Path Params**: `groupId*` (UUID)
*   **Output**: `200 OK` (`GroupDashboardResponse`)
    *   Includes `targetCurrencyId`, `userBalances`, `expenses`, and `settlements` arrays.

### 4.6 Get Groups by User
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

### 4.7 Get Group Users
*   **Endpoint**: `GET /api/groups/users/{groupId}`
*   **Description**: Fetch all members (users) of a specific group. The authenticated user must be a member of the group.
*   **Path Params**: `groupId*` (UUID)
*   **Output**: `200 OK` (List of `FriendResponse` objects) or `403 Forbidden` if the user is not in the group.
    ```json
    [
      {
        "userId": "uuid",
        "name": "Member Name",
        "email": "member@example.com",
        "profilePicUrl": "https://link.to/pic.png"
      }
    ]
    ```

### 4.8 Delete Group
*   **Endpoint**: `DELETE /api/groups/{groupId}`
*   **Description**: Delete a group. Only group admins can perform this action. If `hardDelete` is true, all expenses are marked as deleted. If false (default), the group is deleted only if there are unsettled expenses; otherwise, an error is thrown suggesting to use hard delete.
*   **Path Params**: `groupId*` (UUID)
*   **Query Params**: `hardDelete` (boolean, optional, default: false)
*   **Output**: `200 OK` ("Group deleted successfully") or `400 Bad Request` (e.g., "Cannot delete group: There are unsettled expenses. Use hard delete to force deletion.") or `403 Forbidden` (if not an admin)

### 4.9 Get Group Balances
*   **Endpoint**: `GET /api/groups/{groupId}/balances`
*   **Description**: Get the balances tab for a group, showing net balances per user from the current user's perspective and complete transaction history grouped by user pairs. Includes all expenses and settlements.
*   **Path Params**: `groupId*` (UUID)
*   **Output**: `200 OK` (`GroupBalancesResponse`)
    ```json
    {
      "groupId": "uuid",
      "groupName": "Goa Trip",
      "currencyCode": "INR",
      "userBalances": [
        {
          "userId": "uuid",
          "userName": "Friend Name",
          "netBalance": 250.0,
          "balanceStatus": "Owes you"
        }
      ],
      "transactionHistory": [
        {
          "otherUserId": "uuid",
          "otherUserName": "Friend Name",
          "netAmount": 250.0,
          "transactions": [
            {
              "transactionId": "uuid",
              "type": "EXPENSE",
              "description": "Dinner",
              "date": "2023-01-15T12:00:00",
              "amount": 125.0,
              "currencyCode": "INR"
            }
          ]
        }
      ]
    }
    ```

---

## 5. Invitations (`/api/invitations`)

### 5.1 Invite New User
*   **Endpoint**: `POST /api/invitations/invite`
*   **Description**: Invites a new user to the platform. A temporary user account is created with `PENDING_INVITE` status, and an invitation email is sent (currently mocked). If a `groupId` is provided, the invited user is also added to that group as a non-admin member. If the user already exists and is in `PENDING_INVITE` status, the invitation is re-sent.
*   **Input Body** (mandatory fields marked with *):
    ```json
    {
      "email*": "newuser@example.com",
      "name*": "New User",
      "groupId": "optional-group-uuid"
    }
    ```
*   **Output**: `201 Created` ("Invitation sent to newuser@example.com") or `400 Bad Request` (e.g., "User with this email already exists and is not pending invitation.")

### 5.2 Register Invited User
*   **Endpoint**: `POST /api/invitations/register`
*   **Description**: Allows an invited user to complete their registration by setting a password. The user's status is changed from `PENDING_INVITE` to `ACTIVE`.
*   **Input Body** (mandatory fields marked with *):
    ```json
    {
      "token*": "invitation-token-uuid",
      "password*": "newsecurepassword"
    }
    ```
*   **Output**: `200 OK` ("User newuser@example.com registered successfully.") or `400 Bad Request` (e.g., "Invalid or expired invitation token.")

---

## 6. Home View (`/api/home`)

### 6.1 Groups View
*   **Endpoint**: `GET /api/home/groups`
*   **Description**: Aggregates total owed/owe across all groups for the user.
*   **Query Params**: `displayCurrencyId` (optional UUID) - The global currency to use for aggregation. If not provided, uses user's default currency or INR fallback.
*   **Output**: `200 OK` (`HomeGroupsResponse`)
    *   Includes total amounts and a breakdown array of `GroupSummaryDto` objects.

### 6.2 Friends View
*   **Endpoint**: `GET /api/home/friends`
*   **Description**: Aggregates net balances strictly against individual friends across all shared groups.
*   **Query Params**: `displayCurrencyId` (optional UUID) - Same as above.
*   **Output**: `200 OK` (`HomeFriendsResponse`)
    *   Includes total amounts and a breakdown array of `FriendSummaryDto` objects.

---

## 7. Expenses (`/api/groups/{groupId}/expenses`)

### 7.1 Create Expense
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
*   **Output**: `200 OK` (`SettlementResponse`)
    ```json
    {
      "settlementId": "uuid",
      "message": "Settlement created successfully",
      "status": "SUCCESS"
    }
    ```

*   **Notes**: Settlements are stored internally as `Expense` records with type `SETTLEMENT` but are excluded from the `expenses` array in the group dashboard. They appear in the `settlements` array and are included in balance calculations.

### 7.2 Get Expense Details
*   **Endpoint**: `GET /api/groups/{groupId}/expenses/{expenseId}`
*   **Description**: Fetch details of a specific expense.
*   **Path Params**: `groupId*` (UUID), `expenseId*` (UUID)
*   **Output**: `200 OK` (`ExpenseDetailsResponse`)

---

## 8. Settlements (`/api/settlements`)

### 8.1 Record Settlement (Payment)
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
