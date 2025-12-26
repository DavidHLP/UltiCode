import {
  Controller,
  Get,
  Post,
  Patch,
  Delete,
  Body,
  Param,
  UseGuards,
  Req,
} from '@nestjs/common';
import type { Request } from 'express';
import { CollectionService } from './collection.service';
import { AuthGuard } from '../auth/auth.guard';
import { CreateCollectionDto } from './dto/create-collection.dto';
import { UpdateCollectionDto } from './dto/update-collection.dto';
import {
  AddItemDto,
  UpdateItemDto,
  ReorderCollectionsDto,
} from './dto/collection-item.dto';
import { CollectionTargetType } from '@prisma/client';

interface AuthenticatedRequest extends Request {
  user: { id: string };
}

@Controller('collections')
@UseGuards(AuthGuard)
export class CollectionController {
  constructor(private readonly collectionService: CollectionService) {}

  @Get()
  async getUserCollections(@Req() req: AuthenticatedRequest) {
    return this.collectionService.getUserCollections(req.user.id);
  }

  @Get(':id')
  async getCollection(
    @Req() req: AuthenticatedRequest,
    @Param('id') id: string,
  ) {
    return this.collectionService.getCollectionWithItems(req.user.id, id);
  }

  @Post()
  async createCollection(
    @Req() req: AuthenticatedRequest,
    @Body() dto: CreateCollectionDto,
  ) {
    return this.collectionService.createCollection(req.user.id, dto);
  }

  @Patch(':id')
  async updateCollection(
    @Req() req: AuthenticatedRequest,
    @Param('id') id: string,
    @Body() dto: UpdateCollectionDto,
  ) {
    return this.collectionService.updateCollection(req.user.id, id, dto);
  }

  @Delete(':id')
  async deleteCollection(
    @Req() req: AuthenticatedRequest,
    @Param('id') id: string,
  ) {
    await this.collectionService.deleteCollection(req.user.id, id);
    return { success: true };
  }

  @Post(':id/items')
  async addItem(
    @Req() req: AuthenticatedRequest,
    @Param('id') collectionId: string,
    @Body() dto: AddItemDto,
  ) {
    return this.collectionService.addItem(req.user.id, collectionId, dto);
  }

  @Delete(':id/items/:itemId')
  async removeItem(
    @Req() req: AuthenticatedRequest,
    @Param('id') collectionId: string,
    @Param('itemId') itemId: string,
  ) {
    await this.collectionService.removeItem(req.user.id, collectionId, itemId);
    return { success: true };
  }

  @Delete(':id/items/target/:targetType/:targetId')
  async removeItemByTarget(
    @Req() req: AuthenticatedRequest,
    @Param('id') collectionId: string,
    @Param('targetType') targetType: CollectionTargetType,
    @Param('targetId') targetId: string,
  ) {
    await this.collectionService.removeItemByTarget(
      req.user.id,
      collectionId,
      targetType,
      targetId,
    );
    return { success: true };
  }

  @Patch(':id/items/:itemId')
  async updateItem(
    @Req() req: AuthenticatedRequest,
    @Param('id') collectionId: string,
    @Param('itemId') itemId: string,
    @Body() dto: UpdateItemDto,
  ) {
    return this.collectionService.updateItem(
      req.user.id,
      collectionId,
      itemId,
      dto,
    );
  }

  @Get('item/:targetType/:targetId')
  async getItemCollections(
    @Req() req: AuthenticatedRequest,
    @Param('targetType') targetType: CollectionTargetType,
    @Param('targetId') targetId: string,
  ) {
    return this.collectionService.getItemCollections(
      req.user.id,
      targetType,
      targetId,
    );
  }

  @Post('reorder')
  async reorderCollections(
    @Req() req: AuthenticatedRequest,
    @Body() dto: ReorderCollectionsDto,
  ) {
    await this.collectionService.reorderCollections(
      req.user.id,
      dto.collectionIds,
    );
    return { success: true };
  }
}
