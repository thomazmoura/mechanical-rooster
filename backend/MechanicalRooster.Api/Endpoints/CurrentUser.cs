using System.Security.Claims;
using MechanicalRooster.Api.Data;
using MechanicalRooster.Api.Models;

namespace MechanicalRooster.Api.Endpoints;

public static class CurrentUser
{
    public static Guid GetUserId(this ClaimsPrincipal principal)
    {
        var sub = principal.FindFirstValue(ClaimTypes.NameIdentifier)
                  ?? principal.FindFirstValue("sub")
                  ?? throw new InvalidOperationException("Authenticated principal has no sub claim.");
        return Guid.Parse(sub);
    }

    public static async Task<User?> GetUserAsync(this ClaimsPrincipal principal, AppDbContext db) =>
        await db.Users.FindAsync(principal.GetUserId());
}
