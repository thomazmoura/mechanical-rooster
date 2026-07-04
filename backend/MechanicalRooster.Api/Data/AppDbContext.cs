using MechanicalRooster.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace MechanicalRooster.Api.Data;

public class AppDbContext(DbContextOptions<AppDbContext> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>();
    public DbSet<TaskItem> Tasks => Set<TaskItem>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<User>(user =>
        {
            user.HasIndex(u => u.GoogleSub).IsUnique();
            user.Property(u => u.Email).HasMaxLength(320);
            user.Property(u => u.Name).HasMaxLength(200);
        });

        modelBuilder.Entity<TaskItem>(task =>
        {
            task.Property(t => t.Title).HasMaxLength(500);
            task.HasIndex(t => new { t.UserId, t.CompletedAt });
            task.HasOne(t => t.User)
                .WithMany(u => u.Tasks)
                .HasForeignKey(t => t.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }
}
