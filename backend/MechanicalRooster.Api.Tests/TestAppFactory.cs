using MechanicalRooster.Api.Auth;
using MechanicalRooster.Api.Data;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.Extensions.DependencyInjection;

namespace MechanicalRooster.Api.Tests;

public class FakeGoogleTokenValidator : IGoogleTokenValidator
{
    // Tokens are formatted "sub|email|name" so each test can mint identities.
    public Task<GoogleIdentity?> ValidateAsync(string idToken)
    {
        var parts = idToken.Split('|');
        GoogleIdentity? identity = parts.Length == 3
            ? new GoogleIdentity(parts[0], parts[1], parts[2])
            : null;
        return Task.FromResult(identity);
    }
}

public class TestAppFactory : WebApplicationFactory<Program>
{
    private readonly SqliteConnection _connection = new("DataSource=:memory:");

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseSetting("Database:AutoMigrate", "false");
        builder.UseSetting("Jwt:Key", "test-signing-key-test-signing-key-0123456789");

        _connection.Open();

        builder.ConfigureServices(services =>
        {
            services.RemoveAll(typeof(DbContextOptions<AppDbContext>));
            services.RemoveAll(typeof(IDbContextOptionsConfiguration<AppDbContext>));
            services.AddDbContext<AppDbContext>(options => options.UseSqlite(_connection));

            services.RemoveAll(typeof(IGoogleTokenValidator));
            services.AddScoped<IGoogleTokenValidator, FakeGoogleTokenValidator>();

            using var scope = services.BuildServiceProvider().CreateScope();
            scope.ServiceProvider.GetRequiredService<AppDbContext>().Database.EnsureCreated();
        });
    }

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);
        _connection.Dispose();
    }
}

file static class ServiceCollectionExtensions
{
    public static void RemoveAll(this IServiceCollection services, Type serviceType)
    {
        foreach (var descriptor in services.Where(d => d.ServiceType == serviceType).ToList())
        {
            services.Remove(descriptor);
        }
    }
}
