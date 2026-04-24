# chip-in-service
Services for ChipIn, An app to share and manage expenses among groups!

## API Contract & Swagger
The project now includes Swagger (OpenAPI 3) for API exploration. Once the application is running, access the documentation at:
**[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

You can also view a markdown summary of the API endpoints in the `API_CONTRACT.md` file located at the root of the repository.

## Current Development Status

### Phase 1: Core Foundation (Completed)
- Set up foundational Entities (User, Group, Expense, Currencies) with basic JPA configurations.
- Implemented global currencies management with soft deletes.
- Setup `version` fields for Optimistic Locking across all core tables.

### Phase 2: Group Management and Validation (Completed)
- Groups can now only be created with global active currencies.
- Custom currencies (e.g., "Airport Yen") can be tied uniquely to groups.
- Expenses automatically validate whether the used currency is permitted within the specific group context.

### Phase 3: Aggregates and Settlements (Completed)
- **Settlements**: Added functionality to record payments between users, natively storing them as `EXPENSE` entities with `Type.SETTLEMENT` to cancel out pending debts cleanly.
- **Aggregates/Currency Fix**: Transitioned Group Dashboards and DTOs to utilize exact Currency `UUID` mappings (`targetCurrencyId`) rather than relying on brittle strings (e.g., "INR") for fetching aggregates. Default fallback aligns with the group's creation currency.

### Phase 4: Home Page Views (Completed)
- **Groups View API**: `GET /api/home/groups` - Returns all groups the user is part of, including total money owed/owed to the user, and specific aggregates broken down per group. Uses a global display currency.
- **Friends View API**: `GET /api/home/friends` - Returns a list of all friends the user has shared expenses with across all groups, the exact net balance with each friend, and the overall net balance.

### Phase 5: Profile Updates & API Contract (Completed)
- Added `PUT /api/users/me` to allow users to update their Profile Picture, Name, and Phone.
- Added `GET /api/users/me` to fetch the currently authenticated user's details.
- Integrated `springdoc-openapi-starter-webmvc-ui` (Swagger) for interactive API testing and generated `API_CONTRACT.md` for UI Integration.
