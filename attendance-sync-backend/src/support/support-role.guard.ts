import { CanActivate, ExecutionContext, ForbiddenException, Injectable } from '@nestjs/common';
import { Request } from 'express';

export enum SupportRole {
  SCHOOL_ADMIN = 'SCHOOL_ADMIN',
  SCHOOLLOG_SUPPORT = 'SCHOOLLOG_SUPPORT',
  SUPER_ADMIN = 'SUPER_ADMIN',
}

const allowedRoles = new Set<string>(Object.values(SupportRole));

@Injectable()
export class SupportRoleGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request & { supportRole?: SupportRole }>();
    const role = request.header('x-schoollog-support-role');
    if (!role || !allowedRoles.has(role)) {
      throw new ForbiddenException('Support role is required');
    }
    request.supportRole = role as SupportRole;
    return true;
  }
}
