import { Test, TestingModule } from '@nestjs/testing';
import { I18nService } from './i18n.service';
import { PrismaService } from '../prisma.service';
import { SupportedLocale, TRANSLATABLE_ENTITIES } from './i18n.constants';
import { ConflictException } from '@nestjs/common';
import { Prisma } from '@prisma/client';

type TranslatableEntity = keyof typeof TRANSLATABLE_ENTITIES;

describe('I18nService', () => {
  let service: I18nService;
  let prisma: jest.Mocked<PrismaService>;
  const mockLogger = {
    warn: jest.fn(),
    log: jest.fn(),
    error: jest.fn(),
    debug: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        I18nService,
        {
          provide: PrismaService,
          useValue: {
            translation: {
              findMany: jest.fn().mockResolvedValue([]),
              findUnique: jest.fn().mockResolvedValue(null),
              createMany: jest.fn().mockResolvedValue({ count: 0 }),
            },
          },
        },
      ],
    }).compile();

    service = module.get<I18nService>(I18nService);
    prisma = module.get(PrismaService);

    // Replace the logger with our mock
    (service as any).logger = mockLogger;
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('parseAcceptLanguage', () => {
    it('should return default locale when header is empty', () => {
      const result = service.parseAcceptLanguage(undefined);
      expect(result).toBe('zh-CN');
    });

    it('should parse Accept-Language header', () => {
      const result = service.parseAcceptLanguage('zh-CN,zh;q=0.9,en;q=0.8');
      expect(result).toBe('zh-CN');
    });

    it('should return default locale for unsupported language', () => {
      const result = service.parseAcceptLanguage('fr-FR,fr;q=0.9');
      expect(result).toBe('zh-CN');
    });

    it('should respect quality values', () => {
      const result = service.parseAcceptLanguage('en;q=0.5,zh-CN;q=0.9');
      expect(result).toBe('zh-CN');
    });
  });

  describe('getTranslations', () => {
    it('should return translations for entity', async () => {
      (prisma.translation.findMany as jest.Mock).mockResolvedValue([
        {
          field_name: 'title',
          content: 'Two Sum',
        },
      ] as never);

      const result = await service.getTranslations(
        'PROBLEM' as TranslatableEntity,
        '1',
        'en-US',
      );

      expect(result.get('title')).toBe('Two Sum');
    });

    it('should return empty map when no translations', async () => {
      (prisma.translation.findMany as jest.Mock).mockResolvedValue([]);

      const result = await service.getTranslations(
        'PROBLEM' as TranslatableEntity,
        '1',
        'en-US',
      );

      expect(result.size).toBe(0);
    });

    it('should fallback to default locale for missing fields', async () => {
      (prisma.translation.findMany as jest.Mock)
        .mockResolvedValueOnce([
          { field_name: 'title', content: 'Two Sum' },
        ] as never)
        .mockResolvedValueOnce([
          { field_name: 'title', content: 'Default Title' },
        ] as never);

      const result = await service.getTranslations(
        'PROBLEM' as TranslatableEntity,
        '1',
        'zh-CN',
      );

      expect(result.get('title')).toBe('Two Sum');
    });
  });

  describe('getBatchTranslations', () => {
    it('should return translations for multiple entities', async () => {
      (prisma.translation.findMany as jest.Mock).mockResolvedValue([
        {
          entity_id: '1',
          field_name: 'title',
          content: 'Two Sum',
          locale: 'en-US',
        },
        {
          entity_id: '2',
          field_name: 'title',
          content: 'Three Sum',
          locale: 'en-US',
        },
      ] as never);

      const result = await service.getBatchTranslations(
        'PROBLEM' as TranslatableEntity,
        ['1', '2'],
        'en-US',
      );

      expect(result.get('1')?.get('title')).toBe('Two Sum');
      expect(result.get('2')?.get('title')).toBe('Three Sum');
    });

    it('should return empty map for empty entity IDs', async () => {
      const result = await service.getBatchTranslations(
        'PROBLEM' as TranslatableEntity,
        [],
        'en-US',
      );

      expect(result.size).toBe(0);
    });

    it('should override fallback with requested locale', async () => {
      (prisma.translation.findMany as jest.Mock).mockResolvedValue([
        {
          entity_id: '1',
          field_name: 'title',
          content: 'Two Sum (fallback)',
          locale: 'en-US',
        },
        {
          entity_id: '1',
          field_name: 'title',
          content: '两数之和',
          locale: 'zh-CN',
        },
      ] as never);

      const result = await service.getBatchTranslations(
        'PROBLEM' as TranslatableEntity,
        ['1'],
        'zh-CN',
      );

      expect(result.get('1')?.get('title')).toBe('两数之和');
    });
  });

  describe('applyTranslations', () => {
    it('should apply translations to entity', () => {
      const entity = {
        id: 1,
        title: 'Two Sum',
        description: 'Default description',
      };

      const translations = new Map([
        ['title', '两数之和'],
        ['description', '两数之和描述'],
      ]);

      const result = service.applyTranslations(entity, translations, [
        'title',
        'description',
      ]);

      expect(result.title).toBe('两数之和');
      expect(result.description).toBe('两数之和描述');
    });

    it('should not override fields without translations', () => {
      const entity = {
        id: 1,
        title: 'Two Sum',
        difficulty: 'Easy',
      };

      const translations = new Map([['title', '两数之和']]);

      const result = service.applyTranslations(entity, translations, [
        'title',
        'description',
      ]);

      expect(result.title).toBe('两数之和');
      expect(result.difficulty).toBe('Easy');
    });

    it('should not modify original entity', () => {
      const entity = {
        id: 1,
        title: 'Two Sum',
      };

      const translations = new Map([['title', '两数之和']]);

      const result = service.applyTranslations(entity, translations, ['title']);

      expect(entity.title).toBe('Two Sum');
      expect(result.title).toBe('两数之和');
      expect(result).not.toBe(entity);
    });
  });

  describe('bulkUpsertTranslations', () => {
    const sampleTranslations = [
      {
        entityType: 'PROBLEM' as TranslatableEntity,
        entityId: '1',
        fieldName: 'title',
        locale: 'zh-CN' as SupportedLocale,
        content: '两数之和',
      },
      {
        entityType: 'PROBLEM' as TranslatableEntity,
        entityId: '2',
        fieldName: 'title',
        locale: 'zh-CN' as SupportedLocale,
        content: '三数之和',
      },
    ];

    it('should bulk upsert translations and return result', async () => {
      (prisma.translation.createMany as jest.Mock).mockResolvedValue({
        count: 2,
      } as never);

      const result = await service.bulkUpsertTranslations(sampleTranslations);

      expect(prisma.translation.createMany).toHaveBeenCalledWith({
        data: [
          {
            entity_type: 'PROBLEM',
            entity_id: '1',
            field_name: 'title',
            locale: 'zh-CN',
            content: '两数之和',
          },
          {
            entity_type: 'PROBLEM',
            entity_id: '2',
            field_name: 'title',
            locale: 'zh-CN',
            content: '三数之和',
          },
        ],
        skipDuplicates: true,
      });
      expect(result).toEqual({
        created: 2,
        skipped: 0,
        duplicates: [],
      });
    });

    it('should return empty result for empty translations array', async () => {
      const result = await service.bulkUpsertTranslations([]);

      expect(result).toEqual({
        created: 0,
        skipped: 0,
        duplicates: [],
      });
      expect(prisma.translation.createMany).not.toHaveBeenCalled();
    });

    it('should skip duplicates when skipDuplicates is true', async () => {
      (prisma.translation.createMany as jest.Mock).mockResolvedValue({
        count: 1,
      } as never);

      const result = await service.bulkUpsertTranslations(sampleTranslations, {
        skipDuplicates: true,
        logSkipped: true,
      });

      expect(result.created).toBe(1);
      expect(result.skipped).toBe(1);
      expect(mockLogger.warn).toHaveBeenCalledWith(
        'Skipped 1 duplicate translations during bulk upsert',
      );
    });

    it('should not log when logSkipped is false', async () => {
      (prisma.translation.createMany as jest.Mock).mockResolvedValue({
        count: 1,
      } as never);

      const result = await service.bulkUpsertTranslations(sampleTranslations, {
        skipDuplicates: true,
        logSkipped: false,
      });

      expect(result.created).toBe(1);
      expect(result.skipped).toBe(1);
      expect(mockLogger.warn).not.toHaveBeenCalled();
    });

    it('should throw ConflictException when skipDuplicates is false and duplicates exist', async () => {
      // Mock findMany to return a duplicate that matches the first sample translation
      (prisma.translation.findMany as jest.Mock).mockResolvedValue([
        {
          entity_type: 'PROBLEM',
          entity_id: '1',
          field_name: 'title',
          locale: 'zh-CN',
        },
      ] as never);

      await expect(
        service.bulkUpsertTranslations(sampleTranslations, {
          skipDuplicates: false,
        }),
      ).rejects.toThrow(ConflictException);

      expect(prisma.translation.createMany).not.toHaveBeenCalled();
    });

    it('should handle Prisma unique constraint error for single translation', async () => {
      const prismaError = new Prisma.PrismaClientKnownRequestError(
        'Unique constraint violation',
        {
          code: 'P2002',
          clientVersion: '5.0.0',
        },
      );

      (prisma.translation.createMany as jest.Mock).mockRejectedValue(
        prismaError,
      );

      await expect(
        service.bulkUpsertTranslations([sampleTranslations[0]]),
      ).rejects.toThrow(ConflictException);
    });

    it('should pass through other errors', async () => {
      const otherError = new Error('Database connection failed');
      (prisma.translation.createMany as jest.Mock).mockRejectedValue(
        otherError,
      );

      await expect(
        service.bulkUpsertTranslations([sampleTranslations[0]]),
      ).rejects.toThrow('Database connection failed');
    });
  });
});
